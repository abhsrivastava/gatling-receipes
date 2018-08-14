package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class ElasticSimulation extends Simulation {

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
            .body(StringBody("""{"name": "${RandomName}", "age": ${RandomAge}}""")).asJSON
    )
    .pause(5)
    .exec(
        http("query")
        .get("/test/_search?pretty&scroll=1m")
        .headers(headers)
        .body(StringBody("""{"size":1000}"""))
        .check(status.is(200), jsonPath("$..took").ofType[Int].lessThan(50))
    )

  before {
    scenario("SetupIndex")
        .exec(
            http("createindex")
                .put("/test")
        )
        .inject(atOnceUsers(1))
        .protocols(httpConf)
  }  

  setUp(
      scn
        .inject(
            constantUsersPerSec(10) during (60 seconds) randomized
        )
        .protocols(httpConf)
  )

  after {
    scenario("DeleteIndex")
        .exec(
            http("deleteindex")
                .delete("/test")
        )
        .inject(atOnceUsers(1))
        .protocols(httpConf)
    println("finished executing cleanup....")
  }
}
