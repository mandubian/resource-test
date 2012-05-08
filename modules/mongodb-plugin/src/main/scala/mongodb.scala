/*
 * Copyright 2012 Stephane Godbillon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.modules.mongodb

import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.MongoDB
import play.api._
import play.api.libs.json._
import play.api.data.resource._
import play.api.data.validation._

class MongoPlugin(app :Application) extends Plugin {
	lazy val helper :MongoHelper = {
		val parsedConf = MongoPlugin.parseConf(app)
		try {
			MongoHelper(parsedConf._1, parsedConf._2)
		} catch {
			case e => throw PlayException("MongoPlugin Initialization Error", "An exception occurred while initializing the MongoPlugin.", Some(e))
		}
	}

	/** Returns the current [[com.mongodb.casbah.MongoConnection]]. */
	def connection :MongoConnection = helper.connection

	/** Returns the current [[com.mongodb.casbah.MongoDB]]. */
	def db :MongoDB = helper.db

	/** Returns the named collection from current db. */
	def collection(name :String) :MongoCollection = try {
		helper.db(name)
	} catch {
		case uhe :java.net.UnknownHostException => throw app.configuration.globalError("MongoPlugin error: The server host '%s' is unknown, please check your conf/application.conf or your network configuration.".format(uhe.getMessage), Some(uhe))
		case e => throw e
	}

	override def onStart {
		Logger info "MongoPlugin starting..."
		Logger.info("MongoPlugin successfully started with db '%s'! Servers:\n\t\t%s".format(helper.dbName, helper.servers.map { s =>
			"[%s -> %s:%s]".format(s.name, s.host, s.port)
		}.mkString("\n\t\t")))
	}
}

/**
* MongoDB access methods.
*/
object MongoPlugin {
	/** Returns the current [[com.mongodb.casbah.MongoConnection]] connection. */
	def connection(implicit app :Application) :MongoConnection = current.connection

	/** Returns the current [[com.mongodb.casbah.MongoDB]]. */
	def db(implicit app :Application) :MongoDB = current.db

	/** Returns the named collection from current db. */
	def collection(name :String)(implicit app :Application) :MongoCollection = current.collection(name)

	/** Returns the current instance of the plugin. */
	def current(implicit app :Application) :MongoPlugin = app.plugin[MongoPlugin] match {
		case Some(plugin) => plugin
		case _ => throw PlayException("MongoPlugin Error", "The MongoPlugin has not been initialized! Please edit your conf/play.plugins file and add the following line: '400:play.modules.mongodb.MongoPlugin' (400 is an arbitrary priority and may be changed to match your needs).")
	}

	/** Returns the current instance of the plugin (from a [[play.Application]] - Scala's [[play.api.Application]] equivalent for Java). */
	def current(app :play.Application) :MongoPlugin = app.plugin(classOf[MongoPlugin]) match {
		case plugin if plugin != null => plugin
		case _ => throw PlayException("MongoPlugin Error", "The MongoPlugin has not been initialized! Please edit your conf/play.plugins file and add the following line: '400:play.modules.mongodb.MongoPlugin' (400 is an arbitrary priority and may be changed to match your needs).")
	}

	private[mongodb] val DEFAULT_HOST = "localhost"
	private[mongodb] val DEFAULT_PORT = 27017

	private def parseConf(app :Application) = {
		(app.configuration.getString("mongodb.db") match {
			case Some(db) => db
			case _ => throw app.configuration.globalError("Missing configuration key 'mongodb.db'!")
		}, app.configuration.getConfig("mongodb.servers") match {
			case Some(config) => {
				config.keys.toList.sorted.map(_.span(_ != '.')._1).toSet.map { name :String =>
					Server(
						name,
						config.getString(name + ".host").getOrElse(MongoPlugin.DEFAULT_HOST),
						config.getInt(name + ".port").getOrElse(MongoPlugin.DEFAULT_PORT)
					)
				}
			}
			case _ => Set(
				Server("_default_", app.configuration.getString("mongodb.host").getOrElse(MongoPlugin.DEFAULT_HOST), app.configuration.getInt("mongodb.port").getOrElse(MongoPlugin.DEFAULT_PORT))
			)
		})
	}

}

/**
* Play Json lib <=> DBObject converter methods (includes implicits).
*/
object MongoJson {
	import org.bson.BSONObject
	import org.bson.types.ObjectId
	import com.mongodb.BasicDBObject
	import com.mongodb.BasicDBList
	import com.mongodb.DBObject
	import java.util.Date
	import scala.collection.JavaConversions
	import com.mongodb.casbah.commons.MongoDBObject

	/** Serializes the given [[com.mongodb.DBObject]] into a [[play.api.libs.json.JsValue]]. */
	def toJson(dbObject: DBObject): JsValue = Json.toJson(dbObject)(BSONObjectFormat)

	/** Deserializes the given [[play.api.libs.json.JsValue]] into a [[com.mongodb.DBObject]]. */
	def fromJson(v: JsValue): DBObject = Json.fromJson[DBObject](v)(BSONObjectFormat)

	/** Formatter for [[com.mongodb.DBObject]], handling serialization/deserialisation for DBObjects. */
	implicit object BSONObjectFormat extends Format[DBObject] {
		def reads(json: JsValue): DBObject = parse(json.asInstanceOf[JsObject])
		def writes(bson: DBObject): JsValue = Json.parse(bson.toString)

		private def parse(map: JsObject): DBObject = {
      val builder = MongoDBObject.newBuilder

      map.fields.map { case(key, field) => 
      	val f = field match {
					case v: JsObject => {
						specialMongoJson(v).fold (
							normal => parse(normal),
							special => special
						)
					}
					case v: JsArray => { parseArr(v) }
					case v: JsValue => { parseVal(v) }
				}
      	builder += (key -> f)
			}
			builder.result
		}

		private def specialMongoJson(json: JsObject) :Either[JsObject, Object] = {
			if(json.fields.length > 0) {
				json.fields(0) match {
					case (k, v :JsString) if k == "$date" => Right(new Date(v.value.toLong))
					case (k, v :JsString) if k == "$oid" => Right(new ObjectId( v.value ))
					case (k, _) if k.startsWith("$") => throw new RuntimeException("unsupported specialMongoJson " + k)
					case _ => Left(json)
				}
			} else Left(json)
			
		}

		private def parseArr(array: JsArray) :BasicDBList = {
			val r = new BasicDBList()
			r.addAll(scala.collection.JavaConversions.seqAsJavaList(array.value map { v =>
				parseVal(v).asInstanceOf[Object]
			}))
			r
		}

		private def parseVal(v: JsValue): Any = v match {
			case v: JsObject => parse(v)
			case v: JsArray => parseArr(v)
			case v: JsString => v.value
			case v: JsNumber => v.value.doubleValue
			case v: JsBoolean => v.value
			case JsNull => null
			case v: JsUndefined => null
		}
	}

	implicit object ObjectIdFormat extends Format[ObjectId] {
		def reads(json: JsValue) :ObjectId = {
			json match {
				case obj: JsObject if obj.keys.contains("$oid") => new ObjectId( (obj \ "$oid").toString )
				case s: JsString => new ObjectId(s.value)
				case _ => throw new RuntimeException("unsupported ObjectId " + json)
			}
		}
		def writes(objectId: ObjectId) :JsObject = {
			JsObject(Seq("$oid" -> JsString(objectId.toString)))
		}
	}

	
	implicit object MongoDateFormat extends Format[Date] {
		def reads(json: JsValue) :Date = json match {
			case obj: JsObject if obj.keys.contains("$date") => new Date((obj \ "$date").toString.toLong)
			case _ => throw new RuntimeException("unsupported Date " + json)
		}
		def writes(date: Date) :JsObject = JsObject( Seq("$date" -> JsString(date.getTime + "")) )
	}
}

private[mongodb] case class MongoHelper(dbName :String, servers: Set[Server]) {
	import com.mongodb.ServerAddress
	lazy val connection :MongoConnection = {
		if(servers.size > 1)
			MongoConnection(servers.map { server =>
				new ServerAddress(server.host, server.port)
			}.toList)
		else MongoConnection(servers.head.host, servers.head.port)
	}

	def db :MongoDB = connection(dbName)
	def collection(name :String) :MongoCollection = db(name)
}

private[mongodb] case class Server(
	name: String,
	host :String = MongoPlugin.DEFAULT_HOST,
	port :Int = MongoPlugin.DEFAULT_PORT
)

case class MongoTemplate(collection: String) extends ResourceTemplate[JsValue] {
	import com.mongodb.DBObject
	import play.api.Play.current

	val coll = MongoPlugin.collection(collection)

	def create(json: JsValue): ResourceResult[JsValue] = {
		coll.insert(MongoJson.fromJson(json))
		ResourceSuccess(JsNull)
	}

  def fetch(json: JsValue): ResourceResult[JsValue] = {
  	coll.findOne(MongoJson.fromJson(json))
			.map { x:DBObject => ResourceSuccess(MongoJson.toJson(x)) }
			.getOrElse( ResourceOpError(Seq(ResourceErrorMsg("RESOURCE.ERROR", "resource.notfound")) ) )
  }
} 


