import play.api._

import actors._
import java.util.concurrent._
import play.api.libs.json._
import play.api.libs.json.Json.toJson
import play.modules.mongodb._

import play.api.Play.current

object Global extends GlobalSettings {
  
  override def onStart(app: Application) {
    Logger.info("Start")
    
  }
  
  override def onStop(app: Application) {
    Logger.info("Stop")
  }
  
}
