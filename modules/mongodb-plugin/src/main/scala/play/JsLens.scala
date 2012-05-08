package play.api.libs.json

/**
* Lenses
*/

trait Lens[A,B]{
  self =>

  def get: A => B
  def set: (A,B) => A

  def mod(a:A, f: B => B) : A = set(a, f(get(a)))

  def compose[C,L <: Lens[C,B]](that: Lens[C,A])(implicit cons:LensConstructor[C,B,L]):L = cons(
    c => get(that.get(c)),
    (c, b) => that.mod(c, set(_, b))
  )

  def andThen[C,L <: Lens[A,C]](o: Lens[B,C])(implicit cons:LensConstructor[A,C,L]) = o.compose(self)(cons)

}

trait LensConstructor[A,B,L <: Lens[A,B]] extends (( A => B, (A,B) => A) => L)

trait PriorityOne {
  implicit def lens[A,B,L <: Lens[A,B]] = new LensConstructor[A,B,Lens[A,B]] {

    def apply(get: A => B,
              set: (A, B) => A) = Lens(get,set)

  }
}

object LensConstructor extends PriorityOne {

  implicit val jsValueLens = new LensConstructor[JsValue,JsValue,JsValueLens] {

    def apply(get: JsValue => JsValue,
              set: (JsValue, JsValue) => JsValue) = JsValueLens(get,set)

  }
}

object Lens {
  def apply[A,B](getter: A => B,setter: (A, B) => A) = new Lens[A,B] {
    def get = getter
    def set = setter
  }
}

case class JsValueLens(getter: JsValue => JsValue,
                setter: (JsValue, JsValue) => JsValue) extends Lens[JsValue,JsValue]{

  def get = getter
  def set = setter

  def apply(whole: JsValue) = get(whole)

  def apply(whole: JsValue, repl: JsValue) = set(whole, repl)

  def \(f: String) = JsValueLens(
    JsValueLens.objectGetter(get)(f),
    JsValueLens.objectSetter(get)(set)(f))

  def at(i: Int) = JsValueLens(
    JsValueLens.arrayGetter(get)(i),
    JsValueLens.arraySetter(get)(set)(i))

  def as[A](implicit format:Format[A]):Lens[JsValue,A] = Lens[JsValue,A](
    getter = jsValue => format.reads(get(jsValue)),
    setter = (me, value) => set(me, format.writes(value)) )

  def asEither[A](implicit format:Format[A]): Lens[JsValue,Either[String, A]] = Lens[JsValue,Either[String, A]](
    getter = jsValue => get(jsValue) match {
      case JsUndefined(e) => Left(e)
      case e => Right(format.reads(e))
    },
    setter = (me, value) => value match {
      case Left(_) => me
      case Right(v) => set(me, format.writes(v))
    })

  def asOpt[A](implicit format:Format[A]): Lens[JsValue,Option[A]] =
    this.asEither andThen Lens[Either[String,A],Option[A]](
      getter = jsValue => jsValue match {
        case Left(_) => None
        case Right(v) => Some(v)
      },
      setter = (me, value) => value match {
        case None => me
        case Some(v) => me match {
          case l if l.isLeft => l
          case Right(_) => Right[String,A](v)
        }
      })

  def \\(whole: JsValue,
         selector: JsValue => Boolean,
         cb: JsValue => JsValue): JsValue = {
    val elements = JsValueLens.selectAll(get(whole), selector).map{
      t => ((this andThen t._1) -> t._2)
    }

    elements.foldLeft(whole)((acc, t) => {
      val lens = t._1
      val previous = t._2

      lens.set(acc, cb(previous))
      })
  }
}

object JsValueLens {
  def init = JsValueLens(a => a, (_, a) => a)
  def self = JsValueLens(a => a, (a, _) => a)

  val L0 = init

  def \(f: String): JsValueLens = JsValueLens(
    objectGetter(a => a)(f),
    objectSetter(a => a)((_, a) => a)(f)
  )

  def at(i: Int): JsValueLens = JsValueLens(
    arrayGetter(a => a)(i),
    arraySetter(a => a)((_, a) => a)(i)
  )

  private[JsValueLens] def objectGetter
    (get: JsValue => JsValue)
    (f: String): (JsValue => JsValue) = (a => get(a) match {
        case JsObject(fields) =>
          fields
            .find(t => t._1 == f)
            .getOrElse(("undefined" -> JsUndefined("Field " + f + " not found")))
            ._2
        case _ => JsUndefined("Element is not an object, couldn't find field "+f)
      })

  private[JsValueLens] def arrayGetter
    (get: JsValue => JsValue)
    (i: Int): (JsValue => JsValue) = (a => {
      get(a) match {
        case JsArray(fields) => fields
          .lift(i)
          .getOrElse(JsUndefined("Index " + i + " not found"))
        case _ => JsUndefined("Element is not an array, couldn't find index " + i)
      }
    })

  private[JsValueLens] def objectSetter
    (get: JsValue => JsValue)
    (set: (JsValue, JsValue) => JsValue)
    (f: String): ((JsValue, JsValue) => JsValue) = (whole, repl) => {
      get(whole) match {
        case JsObject(fields) => {
          val found = fields.find(t => t._1 == f) match {
            case None => false
            case _ => true
          }
          set(whole, JsObject(found match {
            case false => fields :+ (f -> repl)
            case true => fields.map{
              t => t match {
                case (k: String, v: JsValue) => if(k == f){
                  k -> repl
                }else{
                  k -> v
                }
              }
            }
          }))
        }
        case _ => {
          set(whole, JsObject(Seq(f -> repl)))
        }
      }
    }

  private[JsValueLens] def arraySetter
    (get: JsValue => JsValue)
    (set: (JsValue, JsValue) => JsValue)
    (i: Int): ((JsValue, JsValue) => JsValue) = (whole, repl) => {
      get(whole) match {
        case JsArray(fields) => {
          val found = fields.lift(i) match {
            case None => false
            case _ => true
          }
          set(whole, JsArray(found match {
            case false => fields :+ repl
            case true => fields.patch(i, Seq(repl), 1)
          }))
        }
        case _ => {
          set(whole, JsArray(Seq(repl)))
        }
      }
    }

  def selectAll(whole: JsValue, selector: JsValue => Boolean, depth: Int = Int.MaxValue): Seq[(JsValueLens,
  JsValue)] = {
    val baseLens = JsValueLens.init

    val elements = if(selector(whole)){
      Seq(baseLens -> whole)
    } else {
      Nil
    }

    depth match {
      case v if v <= 0 => elements
      case _ => elements ++ (whole match {
        case JsObject(fields) => {
          fields.flatMap{ tuple => {
              val lens = baseLens \ tuple._1

              JsValueLens.selectAll(tuple._2, selector, depth - 1).map{
                t => ((lens andThen t._1) -> t._2)
              }
            }
          }
        }
        case JsArray(fields) => {
          val baseLens = JsValueLens.init
          fields.zipWithIndex.flatMap{
            tuple => {
              val lens = baseLens at tuple._2

              JsValueLens.selectAll(tuple._1, selector, depth - 1).map{
                t => ((lens andThen t._1) -> t._2)
              }
            }
          }
        }
        case _ => Nil
      })
    }
  }

}

