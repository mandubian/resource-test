package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.BodyParsers.parse._

import play.api.libs.json._
import play.modules.mongodb._
import play.modules.mongodb.MongoPlugin._

import play.api.Play._

import com.mongodb.DBObject
import play.api.data.resource._

import play.api.data.validation.Constraints._
import play.api.data.resource.Validators._


object UserController extends ResourceController(
  Resource[JsValue](MongoTemplate("user"))
)

object UserController2 extends ResourceController(
  Resource.raw(
    JsLens.ROOT \ "name" -> minLength(5)
  )(MongoTemplate("user"))
)

object UserController3 extends ResourceController(
  Resource[JsValue](MongoTemplate("user"))
    .checking(JsLens.ROOT \ "name" -> minLength(5))
    .transformQuery( json => json )
    .transformInput( json => json )
    .transformOutput( json => json )
)

object UserController4 extends ResourceController(
  Resource[JsValue](MongoTemplate("user"))
    .checking(JsLens.ROOT \ "name" -> minLength(5))
    .checking(JsLens.ROOT \ "age" -> max(85))
)


case class User(name: String, age: Int)

object UserController5 extends ResourceController(
  Resource(
    JsLens.ROOT \ "name" -> minLength(5),
    JsLens.ROOT \ "age" -> max(85)
  )(User.apply)(User.unapply)
  (MongoTemplate("user"))
)