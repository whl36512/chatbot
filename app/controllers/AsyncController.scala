package controllers

import akka.actor.ActorSystem
import javax.inject._
import play.api._
import play.api.mvc._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSClient
import scala.util.Failure
import scala.util.Success
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import org.apache.commons.codec.digest.DigestUtils
import scala.util.Random
import play.api.libs.json._
import play.api.libs.json.Reads._ // Custom validation helpers
import play.api.libs.functional.syntax._ // Combinator syntax

/**
 * This controller creates an `Action` that demonstrates how to write
 * simple asynchronous code in a controller. It uses a timer to
 * asynchronously delay sending a response for 1 second.
 *
 * @param actorSystem We need the `ActorSystem`'s `Scheduler` to
 * run code after a delay.
 * @param exec We need an `ExecutionContext` to execute our
 * asynchronous code.
 */
@Singleton
class AsyncController @Inject() (actorSystem: ActorSystem)(implicit exec: ExecutionContext, implicit val messagesApi: MessagesApi, implicit val ws: WSClient) extends Controller with I18nSupport {

  /**
   * Create an Action that returns a plain text message after a delay
   * of 1 second.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/message`.
   */
  def message = Action.async {
    getFutureMessage(1.second).map { msg => Ok(msg) }
  }

  private def getFutureMessage(delayTime: FiniteDuration): Future[String] = {
    val promise: Promise[String] = Promise[String]()
    actorSystem.scheduler.scheduleOnce(delayTime) { promise.success("Hi!") }
    promise.future
  }

  val apiAiUrl = "https://api.api.ai/api/query"

  def chat = Action.async { implicit request =>
    val queryString = request.body.asFormUrlEncoded
    val sessionId = request.session.get("sessionId").getOrElse(DigestUtils.md5Hex(Random.nextLong.toHexString))
    var chatMsg = ""
    queryString match {
      case None => Future { Ok(views.html.chat.POC(chatMsg, null)).withSession(request.session + ("sessionId" -> sessionId)) }
      case qs => {
        val queryString = qs.get.map { case (k, v) => (k, v(0)) } // get form data from POST
        chatMsg = queryString("chatMsg")
        Logger.info("INFO 20170424204601 chatMsg=" + chatMsg)

        var req: WSRequest = ws.url(apiAiUrl)
        Logger.info("INFO 20170424084701 sessionId=" + sessionId + " req = " + req.url)
        req = req.withHeaders("Authorization" -> "Bearer 0ed97d1c6c13484fa3f51cb56be95c85").withQueryString(("v", "20150910"), ("lang", "en"), ("sessionId", sessionId), ("query", chatMsg))
        Logger.info("INFO 20170424204901 req=" + req.toString())
        val futureResponse = req.get()
        futureResponse.map(wsResponse => {
          Ok(views.html.chat.POC(chatMsg, wsResponse)).withSession(request.session + ("sessionId" -> sessionId))
        })
      }
    }
  }

  implicit val rds = (
    (JsPath \\ 'chatMsg).read[String] and
    (JsPath \ 'sessionId).read[String]) tupled

  def chatws = Action.async(parse.json) { implicit request =>
    val a=  request.body.validate[(String, String)]
    a.map{ 
      case (chatMsg, sessionId) =>
        Logger.info("INFO 20170509220301 sessionId=" + sessionId + " chatMsg= " + chatMsg)
        var req: WSRequest = ws.url(apiAiUrl)
        req=req.withHeaders("Authorization" -> "Bearer 0ed97d1c6c13484fa3f51cb56be95c85").withQueryString(("v", "20150910"), ("lang", "en"), ("sessionId", sessionId), ("query", chatMsg))
                Logger.info("INFO 20170424204901 req=" + req.toString())
        val futureResponse = req.get()
        futureResponse.map(wsResponse =>
          Ok(wsResponse.json)
        )      
    }.recoverTotal{
      e => Future{BadRequest("ERROR 20170509215901 " + JsError.toJson(e))}
    }
  }
}

