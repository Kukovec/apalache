package at.forsyte.apalache.tla.assignments

import at.forsyte.apalache.tla.lir._
import com.microsoft.z3._

object SMTInterface extends TypeAliases {

  class FunWrapper( m_fun : FuncInterp, m_varSym : String ) {
    /** Return value for arguments outside the relevant subdomain. */
    protected val m_default : Int = m_fun.getElse.asInstanceOf[IntNum].getInt

    /**
      * Internal map, corresponds to the restriction of the function represented by `m_fun`
      * to the relevant subdomain.
      */
    protected val m_map : Map[String, Int] =
      ( for {e : FuncInterp.Entry <- m_fun.getEntries}
        yield (
          "%s_%s".format( m_varSym, e.getArgs.head ),
          e.getValue.asInstanceOf[IntNum].getInt
        )
        ).toMap

    /** The wrapper can be called like a function. */
    def apply( arg : String ) : Int = m_map.getOrElse( arg, m_default )

    override def toString : String = m_map.toString
  }

  def apply( p_spec : String, p_varSym : String ) : Option[StrategyType] = {

    /** Initialize a context and solver */
    val ctx = new Context()
    val solver = ctx.mkSolver()

    /** Parse the spec and add it to the solver */
    solver.add( ctx.parseSMTLIB2String( p_spec, null, null, null, null ) )

    val status = solver.check.toString
    if ( status != "SATISFIABLE" )
      return None

    /** If SAT, get a model. */
    val m = solver.getModel

    /** Extract the rank function. Should be the only (non-const.) function */
    val fnDecl = m.getFuncDecls

    fnDecl.size match{
      case 0 => /** Only happens if Next is exactly 1 assignment */
        val trues =
          m.getConstDecls.withFilter( x => m.getConstInterp( x ).isTrue ).map( _.getName.toString )
        Some( trues.map( x => UID( x.substring( 2 ).toInt ) )  )

      case 1 =>
        if ( fnDecl.size != 1 )
          return None

        /** Wrap the function so it can be used to sort the sequence later. */
        val wrap = new FunWrapper( m.getFuncInterp( fnDecl( 0 ) ), p_varSym )

        /** Extract all constants which are set to true */
        val trues = m.getConstDecls.withFilter( x => m.getConstInterp( x ).isTrue ).map( _.getName.toString )

        /** Sort by rank */
        val sorted = trues.sortBy( x => wrap( x ) )

        /* return */ Some( sorted.map( x => UID( x.substring( 2 ).toInt ) ) )

      case _ => None
    }
  }

}