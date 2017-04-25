package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import play.api.cache._
import play.api.cache.Cached // for page cache
import play.api.i18n._

//import play.api.db._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.i18n.DefaultMessagesApi
import play.api.i18n.DefaultLangs
import play.api.libs.ws.WSClient
import play.api.libs.ws._
import models._
import play.api.db.Database
import scala.concurrent.duration._
import play.api.Logger

import play.api.libs.mailer._
import java.io.File
import org.apache.commons.mail.EmailAttachment

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class Application @Inject() (implicit mailerClient: MailerClient, implicit val cached: Cached, implicit val cache: CacheApi, implicit val messagesApi: MessagesApi, implicit val db: Database, implicit val ws: WSClient)
    extends Controller with I18nSupport {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  //private val topic = Topic("")
  //https://api.api.ai/api/query?v=20150910&query=hotel&lang=en&sessionId=114a25a8-638e-49d4-be3f-b38692fe5885&timezone=2017-04-24T19:10:08-0500' -H 'Authorization:Bearer d6536d1443384f8298e1ad036b771c91
  def todo = TODO

  def echo = Action { reques =>
    Ok("Got request [" + reques + "]")
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}




