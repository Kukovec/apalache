package at.forsyte.apalache.tla.lir.transformations.standard

import at.forsyte.apalache.tla.lir.{OperEx, TlaEx}
import at.forsyte.apalache.tla.lir.oper.{LetInOper, TlaBoolOper, TlaOper}
import at.forsyte.apalache.tla.lir.transformations.{TlaExTransformation, TransformationTracker}

object Flatten {
  private def sameOp( op : TlaOper )( ex : TlaEx ) : Boolean = ex match {
    case OperEx( o, _ ) => o == op
    case _ => false
  }

  private def flattenOne( tracker : TransformationTracker ) : TlaExTransformation =
    tracker.track {
      case ex@OperEx( TlaBoolOper.and | TlaBoolOper.or, args@_* ) =>
        val filterFun = sameOp( ex.oper )(_)
        // We're looking for cases of OperEx( op1, ..., OperEx( op2, ...),... ) where op1 == op2
        val similar = args.filter {
          filterFun
        }
        // If there are no direct children with the same operator, do nothing
        if ( similar.isEmpty )
          ex
        else {
          // Thhese arguments stay unchanged
          val different = args.filterNot {
            filterFun
          }
          // Then we steal all children from similar (= same and/or oper) OperEx subexpressions
          val flattened = similar.foldLeft( Seq.empty[TlaEx] ) {
            case (a, b) => b.asInstanceOf[OperEx].args ++ a
          }
          // we add the flattened to the existing
          val newArgs = different ++ flattened
          OperEx( ex.oper, newArgs : _* )
        }
      case e => e
    }

  /**
    * Returns a transformation that replaces nested conjunction/disjunction with a flattened equivalent.
    *
    * Example:
    * ( a /\ b) /\ c [/\(/\(a,b),c)] -> a /\ b /\ c [/\(a,b,c)]
    */
  def apply( tracker : TransformationTracker ) : TlaExTransformation = tracker.track { ex =>
    val tr = flattenOne( tracker )
    lazy val self = apply( tracker )
    ex match {
      case OperEx( op : LetInOper, body ) =>
        // Transform bodies of all op.defs
        val replacedOperDecls = op.defs.map { x =>
          x.copy(
            body = self( x.body )
          )
        }

        val newOp = new LetInOper( replacedOperDecls )
        val newBody = self( body )
        val retEx = if ( op == newOp && body == newBody ) ex else OperEx( newOp, newBody )

        tr( retEx )
      case OperEx( op, args@_* ) =>
        val newArgs = args map self
        val newEx = if ( args == newArgs ) ex else OperEx( op, newArgs : _* )
        tr( newEx )
      case _ => tr( ex )
    }
  }
}