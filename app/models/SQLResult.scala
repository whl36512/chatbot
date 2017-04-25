package models

import play.api.mvc._
import play.api.db._
import java.sql.ResultSet
import org.json.JSONArray
import org.json.JSONObject
import scala.collection.mutable.ArrayBuffer




//class SQLReslut @Inject() (db: Database, SQL: String) extends ResultSet {

object SQLResult  {

  def apply(db: Database, SQL: String): JSONArray= {

    val conn = db.getConnection()
    try {
      val stmt = conn.createStatement
      val rs : ResultSet = stmt.executeQuery(SQL)
      return ResultSetConverter.toJSONArray(rs)
    } finally {
      conn.close()
    }
  }  
  
  def apply (db: Database, SQL: String, params: Array[String]) : ArrayBuffer[Map[String, String]]  = {
    val conn = db.getConnection()
    try {
      val stmt = conn.prepareStatement(SQL)
      
      val paramCount= if (params==null)  0 else params.length
      for (i <- 0 until  paramCount)
      {
        stmt.setObject(i+1, params(i))
      }
      stmt.execute()
      val rs = stmt.getResultSet()

      return ResultSetConverter.toMapArray(rs)
    } finally {
      conn.close()
    }

  }
}


/**
 * Utility for converting ResultSets into some Output formats
 */
object ResultSetConverter  {
  /**
   * Convert a result set into a JSON Array
   * @param resultSet
   * @return a JSONArray
   * @throws Exception
   */

  def apply(resultSet: ResultSet)  = {
    //var jsonArray = new JSONArray();
    var jsonArray = ArrayBuffer.empty[JSONObject]
    if (resultSet.next()) {
      val total_cols = resultSet.getMetaData().getColumnCount();

      do {

        var obj = new JSONObject();
        for (i <- 1 to total_cols) {
          obj.put(resultSet.getMetaData().getColumnLabel(i)
            .toLowerCase(), resultSet.getObject(i));
        }
        jsonArray += obj
      } while (resultSet.next())
    }
    jsonArray
  }

  def toJSONArray(resultSet: ResultSet) = {
    var jsonArray = new JSONArray();
    if (resultSet.next()) {
      val total_cols = resultSet.getMetaData().getColumnCount();

      do {

        var obj = new JSONObject();
        for (i <- 1 to total_cols) {
          obj.put(resultSet.getMetaData().getColumnLabel(i)
            .toLowerCase(), resultSet.getObject(i));
        }
        jsonArray.put( obj)
      } while (resultSet.next())
    }
    jsonArray
  }
  
  def toMapArray(resultSet: ResultSet) =
  {
    var table = ArrayBuffer.empty[Map[String, String]]
    if (resultSet == null) table
    else if (resultSet.next()) {
      val total_cols = resultSet.getMetaData().getColumnCount();
      
      do {

        var row =  Map[String, String]()
        for (i <- 1 to total_cols) {
          val colValue= resultSet.getObject(i)
          val colValString= if (colValue == null) "" else colValue.toString 
          row += (resultSet.getMetaData().getColumnLabel(i).toLowerCase() -> colValString)
        }
        table += row
      } while (resultSet.next())
    }
    table
    }
}