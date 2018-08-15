package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random
import com.softwaremill.sttp._
import io.gatling.core.body.{StringBody => GatlingStringBody}

class ElasticGatingSimulation extends Simulation {
  implicit val backend = HttpURLConnectionBackend()  
  val httpConf = http
    .baseURL("http://localhost:9200") 

  val headers = Map("Content-Type" -> "application/json")
  val randomData = Iterator.continually(
    Map(
        "RandomName" -> Random.alphanumeric.take(8).mkString,
        "RandomAge" -> Random.nextInt(100)
    )
  )

  val scn = scenario("LoadAndQuery")
    .feed(randomData)
    .exec(
        http("ingest")
            .post("/test/mytype")
            .headers(headers)
            .body(GatlingStringBody("""{"name": "${RandomName}", "age": ${RandomAge}}""")).asJSON
    )
    .pause(5)
    .exec(
        http("query")
        .get("/test/_search?pretty&scroll=1m")
        .headers(headers)
        .body(GatlingStringBody("""{"size":1000, "query": {"match_all": {}}}"""))
        .check(status.is(200), jsonPath("$..took").ofType[Int].lessThan(50))
    )

  before {
      val request = sttp.put(uri"http://localhost:9200/test")
      val response = request.send()
      println(response.unsafeBody)                     
  }  

  setUp(
      scn
        .inject(
            constantUsersPerSec(10) during (60 seconds) randomized
        )
        .protocols(httpConf)
  )

  after {
    val request = sttp.delete(uri"http://localhost:9200/test")
    val response = request.send()
    println(response.unsafeBody)                     
  }
}
