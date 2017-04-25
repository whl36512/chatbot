package models

import java.net.MalformedURLException
import java.net.URL
import java.sql.Timestamp
import java.text.SimpleDateFormat

import scala.collection.mutable.ArrayBuffer

import models._
import play.api.data._
import play.api.data.Forms._
import play.api.db.Database
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.Date
import play.api.mvc.RequestHeader
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger

//import play.api.libs.ws.WSRequest
//import play.api.libs.ws.WSClient
//import scala.concurrent.ExecutionContext
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Await

case class Link(linkId: String = "", topicId: String = "", title: String = "", publishTS: String = "", createTS: String = "", author: String = "", url: String = "", commentCnt: Int = 0, language: String = "english") {
  // link_id    
  // topic_id   
  // title      
  // publish_ts 
  // create_ts  
  // author     
  // url      
  // language
  def saveTo(db: Database): ArrayBuffer[Map[String, String]] = {
    try {
      val urlObj = new URL(url)
      None
    } catch {
      case e: MalformedURLException => return ArrayBuffer(Map("result" -> "ERROR 20121231: Invalid URL"))
    }

//    try {
//      val dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
//      val parsedDate = dateFormat.parse(publishTS);
//      val timestamp = new java.sql.Timestamp(parsedDate.getTime());
//    } catch {
//      case e: Exception => {
//        try {
//          val dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//          val parsedDate = dateFormat.parse(publishTS);
//          val timestamp = new java.sql.Timestamp(parsedDate.getTime());
//
//        } catch {
//          case e: Exception => return ArrayBuffer(Map("result" -> "ERROR 201231120101: Invalid timestamp"))
//        }
//      }
//
//    }
    SQLResult(db, " select * from save_link(?,?,?,?,?,?)", Array(topicId, title, publishTS, author, url, language))
  }
}

object Link {
  val form = Form(
    mapping(
      "linkId" -> ignored(""), "topicId" -> text, "title" -> text(), "publishTS" -> text(), "createTS" -> ignored(""), "author" -> text(), "url" -> nonEmptyText(minLength = 20), "commentCnt" -> ignored(1), "language" -> text())(Link.apply)(Link.unapply)
      verifying ("WHL ERROR: malformed URL", fields => fields match {
        // link below is an unvalidate Link captured from the mapping
        //        case link => validate(link.linkId, link.topicId, link.title, link.publishTS, link.createTS, link.author, link.url, link.commentCnt).isDefined
        case link => validate(link).isDefined
      }))

  def validate(link: Link) = {
    try {
      val urlObj = new URL(link.url)
      Some(link)

    } catch {
      case e: MalformedURLException => None
    }
  }

  def selectAll(db: Database) = {
    SQLResult(db, "select * from all_link()", null)
  }

  def select(db: Database, linkId: String, topicId: String) = {
    if (linkId == null && topicId == null) selectAll(db)
    else {
      SQLResult(db, "select * from link_order_by_score(?,?)", Array(linkId, topicId))
    }
  }

  def select(db: Database, rh: RequestHeader) =
    {
      val qs = rh.queryString.map { case (k, v) => (k -> v(0)) }
      val searchQuery = qs.get("searchQuery").getOrElse("")
      SQLResult(db, "select * from all_link(?)", Array(searchQuery))
    }

  def getOrgByURL(url: String) = {
    val urlObj = new URL(url)
    urlObj.getAuthority()
  }
  
  def linkInReview(db: Database) = {
    SQLResult(db, "select * from link where status = 'R' order by create_ts desc", Array())
  }
}

case class Msg(msgId: String = "", linkId: String = "", parentMsgId: String = "", createTS: String = "", content: String = "", ipHash: String = "") {
  def saveTo(db: Database) = {
    // save comment and update comment count
    SQLResult(db, "select * from save_msg(?,?,?,?)", Array(linkId, parentMsgId, content, ipHash))
  }

  def flag(db: Database) = {
    val SQL = "update msg set flag_cnt=flag_cnt+1 where msg_id = nullif(?, '')::uuid returning 'flagged as abusive' result"
    val result = SQLResult(db, SQL, Array(msgId))(0).get("result").getOrElse("failed")
    result
  }

  def validate = {
    val MIN = 30
    val MAX = 4000

    val error = (content.length, linkId, parentMsgId) match {
      case (length, _, _) if length < MIN => "ERROR 20170114212148: comment too short. Minimum " + MIN
      case (length, _, _) if length > MAX => "ERROR 20170114212149: comment too long. Maximum " + MAX
      case (_, "", "")                    => "ERROR 20170114212310: Either linkId or parentMsgId must be populated"
      case (_, "null", "null")            => "ERROR 20170114212311: Either linkId or parentMsgId must be populated"
      case _                              => ""
    }

    error match {
      case "" => error // no error
      case e  => { Logger.error(e); e }
    }
  }
}

object Msg {
  // msg_id   
  // link_id  
  // create_ts
  // content  
  val form = Form(
    mapping(
      "msgId" -> ignored(""), "linkId" -> ignored(""), "parentMsgId" -> ignored(""), "createTS" -> ignored(""), "content" -> nonEmptyText(minLength = 20), "ipHash" -> ignored(""))(Msg.apply)(Msg.unapply))

  def selectOrderByTime(db: Database, linkId: String, topicId: String) = {
    SQLResult(db, "select * from msg_order_by_score(?,?)", Array(linkId, topicId))
  }
  def hashIp(ip: String) = {
    DigestUtils.sha1Hex(ip + ip)
  }
}

case class Throttle(val blockPeriod: Long = 10000, penaltyPeriod: Long = 10000, penaltyFactor: Int = 2) { // in millisecond
  var blockTime: Long = 0
  var penaltyTime: Long = 0

  def isBlocked = {
    val activityTime = java.time.Instant.now().toEpochMilli

    activityTime match {
      case t if t <= blockTime => true
      case t if t > blockTime && t <= penaltyTime => {
        Logger.debug("DEBUG 20170114231512: activityTime blockTime penaltyTime =" + activityTime + " " + (blockTime-activityTime) + " "  + ( penaltyTime-activityTime ) )
        blockTime = t + penaltyFactor * (penaltyTime - t) + blockPeriod
        penaltyTime = blockTime - t + blockTime
        Logger.debug("DEBUG 20170114231513: activityTime newblockTime newpenaltyTime =" + activityTime + " " + (blockTime-activityTime) + " "  + ( penaltyTime-activityTime ) )
        false
      }
      case t if t > penaltyTime => {
        Logger.debug("DEBUG 20170114231514: activityTime blockTime penaltyTime =" + activityTime + " " + (blockTime-activityTime) + " "  + ( penaltyTime-activityTime ) )
        blockTime = t + blockPeriod
        penaltyTime = blockTime + penaltyPeriod
        Logger.debug("DEBUG 20170114231515: activityTime newblockTime newpenaltyTime =" + activityTime + " " + (blockTime-activityTime) + " "  + ( penaltyTime-activityTime ) )

        false
      }
    }
  }
}

case class UserThrottle(commentThrottle: Throttle = Throttle(), flagThrottle: Throttle = Throttle(blockPeriod = 5000, penaltyPeriod = 5000))
    
