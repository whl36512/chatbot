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

import models._
import play.api.db.Database

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
class AsyncController @Inject() (actorSystem: ActorSystem)(implicit exec: ExecutionContext, implicit val messagesApi: MessagesApi, implicit val ws: WSClient, implicit val cache: CacheApi, implicit val db: Database) extends Controller with I18nSupport {

  /**
   * Create an Action that returns a plain text message after a delay
   * of 1 second.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/message`.
   */

  //curl 'https://api.api.ai/api/query?v=20150910&query=used&lang=en&sessionId=94148e79-af15-4460-801d-309dabcf66e4&timezone=2017-05-11T14:59:51-0500' -H 'Authorization:Bearer f41377e7b136496c9b6381ced9012be7'

  def message = Action.async {
    getFutureMessage(1.second).map { msg => Ok(msg) }
  }

  private def getFutureMessage(delayTime: FiniteDuration): Future[String] = {
    val promise: Promise[String] = Promise[String]()
    actorSystem.scheduler.scheduleOnce(delayTime) { promise.success("Hi!") }
    promise.future
  }

  val apiAiUrl = "https://api.api.ai/api/query"

  def getBearer(client: String) = {
    val apiAiBearerD = "f41377e7b136496c9b6381ced9012be7" //DaimlerFS
    val apiAiBearerZ = "0ed97d1c6c13484fa3f51cb56be95c85" //zalora

    client match {
      case "z" => apiAiBearerZ
      case "d" => apiAiBearerD
      case c   => apiAiBearerD
    }
  }

  def chat(client: String) = Action.async { implicit request =>

    val apiAiBearer = getBearer(client)

    try {
      val queryString = request.body.asFormUrlEncoded
      val sessionId = request.session.get("sessionId").getOrElse(DigestUtils.md5Hex(Random.nextLong.toHexString))
      var chatMsg = ""
      queryString match {
        case None => Future { Ok(views.html.chat.POC(chatMsg, null)).withSession(request.session + ("sessionId" -> sessionId)) }
        case qs => {
          val queryString = qs.get.map { case (k, v) => (k, v(0)) } // get form data from POST
          chatMsg = queryString("chatMsg")
          Logger.info("INFO 20170424204601 sessionId=" + sessionId + " chatMsg=" + chatMsg)
          chatMsg match {
            case "" => Future { BadRequest("400 Bad Request") }
            case chatMsg => {

              val formInJson = Json.obj("sessionId" -> sessionId, "chatMsg" -> chatMsg)

              callMultipleWS(formInJson, apiAiBearer).map { combinedResponse =>
                Ok(views.html.chat.POC(chatMsg, combinedResponse)).withSession(request.session + ("sessionId" -> sessionId))
              }
            }
          }
        }
      }
    } catch {
      case e: Throwable =>
        Logger.error("ERROR 20170512152702 " + e)
        Future { BadRequest("400 Bad Request") }

    }
  }

  implicit val rds = (
    (JsPath \\ 'chatMsg).read[String] and
    (JsPath \ 'sessionId).read[String]) tupled

  def chatws(client: String) = Action.async(parse.json) { implicit request =>
    val apiAiBearer = getBearer(client)
    try {
      callMultipleWS(request.body, apiAiBearer).map { combinedResponse =>
        Logger.info("INFO 20170510212101 combinedResponse=" + combinedResponse.toString)
        //val contextSeq= getContext(combinedResponse)

        Ok(combinedResponse)
      }
    } catch {
      case e: Throwable =>
        Logger.error("ERROR 20170512152701 " + e)
        Future { BadRequest("400 Bad Request") }
    }
  }

  def getBotResponse(sessionId: String, chatMsg: String, apiAiBearer: String) = {
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

  def callMultipleWS(requestBody: JsValue, apiAiBearer: String) = {
    val jsResult = requestBody.validate[(String, String)]
    jsResult.map {
      case (chatMsg, sessionId) =>
        val futureResponse = getBotResponse(sessionId, chatMsg, apiAiBearer)
        val futureSentiment = sentiment(chatMsg)

        val combined = for { // combine two futured using for comprehension
          chatResponse: WSResponse <- futureResponse
          sentiment: WSResponse <- futureSentiment
        } yield {
          Logger.info("INFO 20170512135601 chatResponse.json=" + chatResponse.json)
          Logger.info("INFO 20170512135602 sentiment.json=" + sentiment)

          val chatActionResult = execChatAction(chatResponse.json)
          val combinedJson = Json.obj("request" -> requestBody, "response" -> chatResponse.json, "sentiment" -> sentiment.json, "actionResult" -> chatActionResult)
          Logger.info("INFO 20170512130601 combinedJson=" + combinedJson)
          combinedJson
        }
        combined
    }.recoverTotal {
      e => Future { JsError.toJson(e) }
    }
  }

  def execChatAction(chatResponse: JsValue) = {
    (chatResponse \ "status" \ "code").as[Int] match {
      case 200 =>
        val chatAction = (chatResponse \ "result" \ "action").as[String]
        chatAction match {
          case "checkTerminationDate"  => checkTerminationDate(chatResponse, db)
          case "changeTerminationDate" => updateTerminationDate(chatResponse, db)
          case action                  => emptyActionResult
        }
      case s => emptyActionResult //bad request

    }
  }

  val emptyActionResult = Json.obj("speech" -> "", "success" -> "no")

  def checkTerminationDate(chatResponse: JsValue, db: Database) = {
    val requestedTerminationDate = (chatResponse \ "result" \ "parameters" \ "requestedTerminationDate").as[String]
    Logger.info("INFO 20170511223701 requestedTerminationDate=" + requestedTerminationDate)
    requestedTerminationDate match {
      case "" => emptyActionResult
      case requestedTerminationDate => {
        val existingTermination = Termination(Termination.existingTerminationDate(db))
        Logger.info("INFO 20170512094702 existingTermination" + existingTermination)
        val actionResultJson = Json.obj("existingTerminationDate" -> existingTermination.terminationDate, "speech" -> ("The existing termination date is " + existingTermination.terminationDate + "."), "success" -> "yes")
        Logger.info("INFO 20170512094703 existingTermination" + actionResultJson)
        actionResultJson
      }
    }
  }

  def updateTerminationDate(chatResponse: JsValue, db: Database) = {
    val requestedTerminationDate = (chatResponse \\ "requestedTerminationDate")(0).as[String]
    Logger.info("INFO 20170511223701 requestedTerminationDate=" + requestedTerminationDate)
    requestedTerminationDate match {
      case "" => Json.obj("requestedTerminationDate" -> "", "speech" -> "Termination date is not changed.", "success" -> "no")
      case requestedTerminationDate => {
        val requestedTermination = Termination(requestedTerminationDate)
        val returnedSpeech = requestedTermination.updateTerminationDate(db)
        Logger.info("INFO 20170512094701 returnedSpeech" + returnedSpeech)

        val actionResultJson = Json.obj("requestedTerminationDate" -> requestedTerminationDate, "speech" -> returnedSpeech, "success" -> "yes")
        Logger.info("INFO 20170512095001 actionResultJson" + actionResultJson)
        actionResultJson
      }
    }
  }
}

