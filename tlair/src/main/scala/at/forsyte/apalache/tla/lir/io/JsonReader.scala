package at.forsyte.apalache.tla.lir.io

import at.forsyte.apalache.tla.lir._
import at.forsyte.apalache.tla.lir.oper.{TlaControlOper, TlaFunOper, TlaOper}
import at.forsyte.apalache.tla.lir.values._

import scala.collection.immutable.HashMap
import scala.collection.mutable.LinkedHashMap

/**
 * <p>A reader of TlaEx and TlaModule from JSON, for interoperability with external tools.</p>
 * @author Andrey Kuprianov
 **/

object JsonReader {

  def read(from: ujson.Readable): TlaEx = {
    val json = ujson.read(from)
    parseJson(json)
  }

  val unaryOps = JsonWriter.unaryOps.map(_.swap)
  val naryOps = JsonWriter.naryOps.map(_.swap)
  val naryPairOps = JsonWriter.naryPairOps.map(_.swap)
  val functionalOps = JsonWriter.functionalOps.map(_.swap)
  val boundedPredOps = JsonWriter.boundedPredOps.map(_.swap)
  val unboundedPredOps = JsonWriter.unboundedPredOps.map(_.swap)
  val stutterOps = JsonWriter.stutterOps.map(_.swap)
  val fairnessOps = JsonWriter.fairnessOps.map(_.swap)
  val otherOps = Set("id", "str", "int", "set", "apply-fun", "apply-op", "IF", "CASE")

  val sets = HashMap(
    "BOOLEAN" -> TlaBoolSet,
    "Int" -> TlaIntSet,
    "Nat" -> TlaNatSet,
    "Real" -> TlaRealSet,
    "STRING" -> TlaStrSet
  )

  // parse arbitrary ujson.Value
  def parseJson(v: ujson.Value): TlaEx = {
    println(v.toString)
    v match {
      case ujson.Str(value) => NameEx(value)
      case ujson.Num(value) =>
        if(value.isValidInt) ValEx(TlaInt(value.toInt))
        else throw new Exception("incorrect TLA+ JSON: wrong number")
      case ujson.Bool(value) => ValEx(TlaBool(value))
      case ujson.Obj(value) => parseExpr(value)
      case _ => throw new Exception("incorrect TLA+ JSON: unexpected input")
    }
  }

  // expect ujson.Value to be an encoding of TLA+ expression
  def parseExpr(m: LinkedHashMap[String, ujson.Value]): TlaEx = {
    val unary = m.keySet & unaryOps.keySet
    val nary = m.keySet & naryOps.keySet
    val naryPair = m.keySet & naryPairOps.keySet
    val functional = m.keySet & functionalOps.keySet
    val boundedPred = m.keySet & boundedPredOps.keySet
    val unboundedPred = m.keySet & unboundedPredOps.keySet
    val stutter = m.keySet & stutterOps.keySet
    val fairness = m.keySet & fairnessOps.keySet
    val other = m.keySet & otherOps
    val ourKeys = unary.size + nary.size + naryPair.size + functional.size +
      + boundedPred.size + unboundedPred.size + stutter.size + fairness.size + other.size
    val expr =
    if(ourKeys < 1)
      throw new Exception("incorrect TLA+ JSON: expected expression, but none found")
    else if(ourKeys > 1)
      throw new Exception("incorrect TLA+ JSON: multiple matching expressions")
    else if(unary.nonEmpty)
      OperEx(unaryOps(unary.head), parseJson(m(unary.head)))
    else if(nary.nonEmpty)
      OperEx(naryOps(nary.head), parseArray(m(nary.head)):_*)
    else if(naryPair.nonEmpty) {
      OperEx(naryPairOps(naryPair.head), parsePairs(m(naryPair.head)) :_*)
    }
    else if(functional.nonEmpty) {
      if(!m.contains("where"))
        throw new Exception("incorrect TLA+ JSON: expecting 'where'")
      OperEx(functionalOps(functional.head), parseJson(m(functional.head)) +: parsePairs(m("where")) :_*)
    }
    else if(unboundedPred.nonEmpty) {
      if(!m.contains("that"))
        throw new Exception("incorrect TLA+ JSON: expecting 'that'")
      OperEx(unboundedPredOps(unboundedPred.head), parseJson(m(unboundedPred.head)), parseJson(m("that")))
    }
    else if(boundedPred.nonEmpty) {
      val nameSet = parsePair(m(boundedPred.head))
      if(!m.contains("that"))
        throw new Exception("incorrect TLA+ JSON: expecting 'that'")
      OperEx(boundedPredOps(boundedPred.head), nameSet(0), nameSet(1), parseJson(m("that")))
    }
    else if(stutter.nonEmpty) {
      if(!m.contains("vars"))
        throw new Exception("incorrect TLA+ JSON: expecting 'vars'")
      OperEx(stutterOps(stutter.head), parseJson(m(stutter.head)), parseJson(m("vars")))
    }
    else if(fairness.nonEmpty) {
      if(!m.contains("vars"))
        throw new Exception("incorrect TLA+ JSON: expecting 'vars'")
      OperEx(fairnessOps(fairness.head), parseJson(m("vars")), parseJson(m(fairness.head)))
    }
    else if(other.nonEmpty) {
      other.head match {
        case "id" => NameEx(parseStr(m("id")))
        case "str" => ValEx(TlaStr(parseStr(m("str"))))
        case "int" => ValEx(TlaInt(BigInt(parseStr(m("int")))))
        case "set" => {
          val set = parseStr(m("set"))
          if(sets.contains(set))
            ValEx(sets(set))
          else
            throw new Exception("can't parse TLA+ JSON: reference to unknown set")
        }
        case "apply-fun" => {
          if(!m.contains("arg"))
            throw new Exception("incorrect TLA+ JSON: expecting 'arg'")
          OperEx(TlaFunOper.app, parseJson(m("apply-fun")), parseJson(m("arg")))
        }
        case "apply-op" => {
          if(!m.contains("args"))
            throw new Exception("incorrect TLA+ JSON: expecting 'args'")
          val name = parseStr(m("apply-op"))
          val args = parseArray(m("args"))
          if(name == "rec-fun-ref") {
            if(args.nonEmpty)
              throw new Exception("incorrect TLA+ JSON: found arguments for 'rec-fun-ref'")
            OperEx(TlaFunOper.recFunRef)
          }
          else
            OperEx(TlaOper.apply, NameEx(name) +: args:_*)
        }
        case "IF" => {
          if(!m.contains("THEN") || !m.contains("ELSE"))
            throw new Exception("incorrect TLA+ JSON: malformed 'IF'")
          OperEx(TlaControlOper.ifThenElse, parseJson(m("IF")), parseJson(m("THEN")), parseJson(m("ELSE")))
        }
        case "CASE" => {
          if(m.contains("OTHER"))
            OperEx(TlaControlOper.caseWithOther, parseJson(m("OTHER")) +: parsePairs(m("CASE")) :_*)
          else
            OperEx(TlaControlOper.caseNoOther, parsePairs(m("CASE")):_*)
        }
        case _ =>
          throw new Exception("can't parse TLA+ JSON: unknown JSON key")
      }
    }
    else
      throw new Exception("can't parse TLA+ JSON: cannot find a known JSON key")
    if(m.contains("label")) {
      val (name, args) = parseLabel(m("label"))
      OperEx(TlaOper.label, expr +: (ValEx(TlaStr(name)) +: args) :_*)
    }
    else
      expr
  }

  // expect ujson.Value to be a string
  def parseStr(v: ujson.Value): String = {
    // it should be a JSON string
    v.strOpt match {
      case Some(value) => value
      case None => throw new Exception("incorrect TLA+ JSON: expecting string")
    }
  }

  // expect ujson.Value to be an encoding of TLA+ expression array
  def parseArray(v: ujson.Value): Seq[TlaEx] = {
    // it should be a JSON array
    val arr = v.arrOpt match {
      case Some(value) => value
      case None => throw new Exception("incorrect TLA+ JSON: expecting expression array")
    }
    arr.map(parseJson)
  }

  // expect ujson.Value to be an encoding of a set of pairs of expressions
  def parsePairs(v: ujson.Value): Seq[TlaEx] = {
    // it should be a JSON array
    val arr = v.arrOpt match {
      case Some(value) => value
      case None => throw new Exception("incorrect TLA+ JSON: expecting array of pairs")
    }
    arr.map(parsePair).flatten
  }

  // expect ujson.Value to be an encoding of a pair of expressions
  def parsePair(v: ujson.Value): Seq[TlaEx] = {
    val pair = parseArray(v)
    if(pair.size != 2)
      throw new Exception("incorrect TLA+ JSON: expecting a pair")
    pair
  }

  // expect ujson.Value to be an encoding of a label
  def parseLabel(v: ujson.Value): (String, Seq[TlaEx]) = {
    // it should be a JSON object
    val m = v.objOpt match {
      case Some(value) => value
      case None => throw new Exception("incorrect TLA+ JSON: expecting a label object")
    }
    if(!m.contains("name") || !m.contains("args"))
      throw new Exception("incorrect TLA+ JSON: malformed label")
    val name = parseStr(m("name"))
    val args = parseArray(m("args"))
    (name, args.map {
      case NameEx(str) => ValEx(TlaStr(str)) // change back from NameEx to ValEx
    })
  }
}

