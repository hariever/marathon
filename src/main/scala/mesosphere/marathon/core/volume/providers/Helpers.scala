package mesosphere.marathon.core.volume.providers

import com.wix.accord._
import com.wix.accord.dsl._
import mesosphere.marathon.core.volume._
import mesosphere.marathon.state._

protected[volume] abstract class AbstractPersistentVolumeProvider(
    val name: String) extends PersistentVolumeProvider[PersistentVolume] {
  /**
    * @return true if volume has a provider name that matches ours exactly
    */
  def accepts(volume: PersistentVolume): Boolean = {
    volume.persistent.providerName.isDefined && volume.persistent.providerName.contains(name)
  }

  override def collect(container: Container): Iterable[PersistentVolume] =
    container.volumes.collect{
      case vol: PersistentVolume if accepts(vol) => vol
    }
}

protected trait OptionSupport {
  import OptionLabelPatterns._

  /** NamedOption represents a (named) configurable item type that provides validation rules */
  abstract class NamedOption(
      val namespace: String,
      val name: String,
      val required: Boolean = false) {
    def fullName: String = namespace + OptionNamespaceSeparator + name
    def from(m: Map[String, String]): Option[String] = m.get(fullName)

    def validOption: Validator[Map[String, String]] = new Validator[Map[String, String]] {
      override def apply(m: Map[String, String]): Result = from(m).map(validValue).getOrElse(
        if (required) Failure(Set(RuleViolation(fullName, "is a required option, but is not present", None)))
        else Success
      )
    }

    val validValue: Validator[String]
  }

  /** supply a validator to enforce that values conform to expectations of "labels" */
  trait NamedLabel {
    val validValue: Validator[String] = validator[String] { v =>
      v should matchRegex(LabelRegex)
    }
  }

  /** supply a validator to enforce that values parse to natural (whole, positive) numbers */
  trait NamedNaturalNumber {
    import scala.util.Try
    val validValue: Validator[String] = new Validator[String] {
      override def apply(v: String): Result = {
        val parsed: Try[Long] = Try(v.toLong)
        if (parsed.isSuccess && parsed.get > 0) Success
        else Failure(Set(RuleViolation(v, s"Expected a valid, positive integer instead of $v", None)))
      }
    }
  }

  /** supply a validator to enforce that values parse to booleans */
  trait NamedBoolean {
    import scala.util.Try
    val validValue: Validator[String] = new Validator[String] {
      override def apply(v: String): Result = {
        val parsed: Try[Boolean] = Try(v.toBoolean)
        if (parsed.isSuccess) Success
        else Failure(Set(RuleViolation(v, s"Expected a valid boolean instead of $v", None)))
      }
    }
  }
}

