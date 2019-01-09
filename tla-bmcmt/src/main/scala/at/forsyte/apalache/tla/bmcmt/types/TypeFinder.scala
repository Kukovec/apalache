package at.forsyte.apalache.tla.bmcmt.types

import at.forsyte.apalache.tla.bmcmt.TypeException
import at.forsyte.apalache.tla.lir.TlaEx

import scala.collection.immutable.SortedMap

/**
  * A diagnostic message that can be thrown as an exception or used in a list of errors.
  *
  * @param origin the expression that caused the type error
  * @param explanation the explanation
  */
class TypeInferenceError(val origin: TlaEx, val explanation: String) extends Exception(explanation)

/**
  * A general interface to a type inference engine. Check the description in docs/types-api.md.
  *
  * @tparam T the base class of the type system
  * @see CellT
  * @author Igor Konnov
  */
trait TypeFinder[T] {
  /**
    * Given a TLA+ expression, reconstruct the types and store them in an internal storage.
    * If the expression is not well-typed, this method will not throw TypeInferenceError,
    * but will collect a list of error that can be access with getTypeErrors.
    *
    * @param e a TLA+ expression.
    * @return Some(type), if successful, and None otherwise
    */
  def inferAndSave(e: TlaEx): Option[T]

  /**
    * Retrieve the type errors from the latest call to inferAndSave.
    * @return a list of type errors
    */
  def getTypeErrors: Seq[TypeInferenceError]

  /**
    * Given a TLA+ expression and the types of its arguments, compute the resulting type, if possible.
    * @param e a TLA+ expression
    * @param argTypes the types of the arguments.
    * @return the resulting type, if it can be computed
    * @throws TypeException, if the type cannot be computed.
    */
  def compute(e: TlaEx, argTypes: T*): T

  /**
    * Get the types of the variables that are computed by inferAndSave. The method must return the types of
    * the global variables (VARIABLE and CONSTANT) and it may return types of the bound variables.
    * @return a mapping of names to types
    */
  def getVarTypes: SortedMap[String, T]

  /**
    * Forget all computed types and introduce types for the variables. You can call inferAndSave after that.
    *
    * @param varTypes types of the global variables (VARIABLE and CONSTANT)
    */
  def reset(varTypes: Map[String, T]): Unit
}
