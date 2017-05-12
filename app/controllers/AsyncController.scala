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
import play.api.libs.ws.WSResponse
import play.api.cache.CacheApi

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
class AsyncController @Inject() (actorSystem: ActorSystem)(implicit exec: ExecutionContext, implicit val messagesApi: MessagesApi, implicit val ws: WSClient,  implicit val cache: CacheApi) extends Controller with I18nSupport {

  /**
   * Create an Action that returns a plain text message after a delay
   * of 1 second.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/message`.
   */

  //curl 'https://api.api.ai/api/query?v=20150910&query=used&lang=en&sessionId=94148e79-af15-4460-801d-309dabcf66e4&timezone=2017-05-11T14:59:51-0500' -H 'Authorization:Bearer f41377e7b136496c9b6381ced9012be7'
  val apiAiBearer = "f41377e7b136496c9b6381ced9012be7" //DaimlerFS
  //  val apiAiBearer ="0ed97d1c6c13484fa3f51cb56be95c85"     //zalora

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
        Logger.info("INFO 20170424204601 sessionId=" + sessionId + " chatMsg=" + chatMsg)
        val formInJson = Json.obj("sessionId" -> sessionId, "chatMsg" -> chatMsg)

        callMultipleWS(formInJson).map { combinedResponse =>
          Ok(views.html.chat.POC(chatMsg, combinedResponse)).withSession(request.session + ("sessionId" -> sessionId))
        }
      }
    }
  }

  implicit val rds = (
    (JsPath \\ 'chatMsg).read[String] and
    (JsPath \ 'sessionId).read[String]) tupled

  def chatws = Action.async(parse.json) { implicit request =>
    callMultipleWS(request.body).map { combinedResponse =>
      Logger.info("INFO 20170510212101 combinedResponse=" + combinedResponse.toString)
      //val contextSeq= getContext(combinedResponse)

      Ok(combinedResponse)
    }
  }

  def getBotResponse(sessionId: String, chatMsg: String) = {
    Logger.info("INFO 20170510140001 sessionId=" + sessionId + " chatMsg= " + chatMsg)

    var req: WSRequest = ws.url(apiAiUrl)
    req = req.withHeaders("Authorization" -> ("Bearer " + apiAiBearer)).withQueryString(("v", "20150910"), ("lang", "en"), ("sessionId", sessionId), ("query", chatMsg))
    Logger.info("INFO 20170510204901 req=" + req.toString())
    req.get
  }

  def sentiment(msg: String) = {
    // curl -d "text=great" http://text-processing.com/api/sentiment/
    val url = "http://text-processing.com/api/sentiment/"
    ws.url(url).post(Map("text" -> Seq(msg)))
  }

  def callMultipleWS(requestBody: JsValue) = {
    val jsResult = requestBody.validate[(String, String)]
    jsResult.map {
      case (chatMsg, sessionId) =>
        val futureResponse = getBotResponse(sessionId, chatMsg)
        val futureSentiment = sentiment(chatMsg)

        val combined = for { // combine two futured using for comprehension
          chatResponse: WSResponse <- futureResponse
          sentiment: WSResponse <- futureSentiment
        } yield {
          val actionResult = execChatAction(chatResponse.json)
          Json.obj("request" -> requestBody, "response" -> chatResponse.json, "sentiment" -> sentiment.json)
        }
        combined
    }.recoverTotal {
      e => Future { JsError.toJson(e) }
    }
  }

  def execChatAction(chatResponse: JsValue) = {
    val chatAction = (chatResponse \ "result" \ "action").as[String]
    chatAction match {
      case "checkTerminationDate" => checkTerminationDate(chatResponse)
      case action                 => TODO
    }
  }

  def checkTerminationDate(chatResponse: JsValue) = {
    val requestedTerminationDate = (chatResponse \ "result" \ "parameters" \ "terminationDate" ).as[String]
    Logger.info("INFO 20170511223701 requestedTerminationDate=" + requestedTerminationDate)
    requestedTerminationDate match {
      case "" => Json.obj()
      case requestedTerminationDate => {
        
      }
      
    }
    
  }
}

