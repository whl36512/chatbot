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
    @volatile var autoResponse: String = ""
    var chatMsg = ""
    queryString match {
      case None => Future{Ok(views.html.chat.POC(chatMsg, autoResponse))}
      case qs => {
        val queryString = qs.get.map { case (k, v) => (k, v(0)) } // get form data from POST
        chatMsg = queryString("chatMsg")
        Logger.info("INFO 20170424204601 chatMsg=" + chatMsg)

        var req: WSRequest = ws.url(apiAiUrl)
        Logger.info("INFO 20170424084701 req = " + req.url)
        req = req.withHeaders("Authorization" -> "Bearer 0ed97d1c6c13484fa3f51cb56be95c85").withQueryString(("v", "20150910"), ("lang", "en"), ("sessionId", "abc"), ("query", chatMsg))
        Logger.info("INFO 20170424204901 req=" + req.toString())
        val futureResponse = req.get()
    //    futureResponse.map(wsResponse => Ok(views.html.chat.POC(chatMsg,  wsResponse.body)))
        futureResponse.map(wsResponse => Ok(views.html.chat.POC(chatMsg, ( wsResponse.json \"result" \  "fulfillment" \ "speech").as[String])))
      }
    }
  }
}

