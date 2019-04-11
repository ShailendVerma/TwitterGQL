package org.shail.twitter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.Language
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import sangria.ast.{Document, FieldDefinition}
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.schema.AstSchemaBuilder.TypeName
import sangria.schema.{Action, AnyFieldResolver, AstSchemaBuilder, Context, FieldResolver, InstanceCheck, ProjectedName, Projector, Schema}
import spray.json.{JsObject, JsString, JsValue}

import scala.io.StdIn
import scala.util.{Failure, Try}

object TwitterGQLApp extends App {


  val logger = Logger("TwitterGQLApp")

  //Config
  val conf = ConfigFactory.load

  //Context Object
  case class QueryContext()

  type Ctx = QueryContext
  type Val = JsValue
  type Res = Any

  val eventBus = TwitterEventsBus.apply

  //implicit val wrtiter = org.shail.twitter.

  // Stream data from twitter based on query field and graphQL API over websockets
  implicit val system = ActorSystem("TwitterGQLSystem")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  //Projection to allow filtering
  def resolveField(fieldName: FieldDefinition) = Projector[Ctx, Val, Res]((ctx: Context[Ctx, Val], proj: Vector[ProjectedName]) => {

    logger.info(s"Processing field : $fieldName")


    Action(None)
  })

  //Schema builder
  val builder = AstSchemaBuilder.resolverBased[Ctx](

    InstanceCheck.field[Ctx, Val],

    FieldResolver {
      case (TypeName("Query"), fieldName) => resolveField(fieldName).asInstanceOf[Context[Ctx, _] => Action[Ctx, _]]
      case (TypeName("Subscription"), fieldName) => resolveField(fieldName).asInstanceOf[Context[Ctx, _] => Action[Ctx, _]]
    },

    AnyFieldResolver.defaultInput[Ctx, Val]

  )

  //Build the schema
  val gqlSchema: Schema[Ctx, Any] =
    Try {
      scala.io.Source.fromResource("twitter.graphql.schema").getLines().mkString("\n")
    }
      .map { schemaStr: String => QueryParser.parse(schemaStr).toOption }
      .map { schemaAst => Schema.buildFromAst(schemaAst.get, builder.validateSchemaWithException(schemaAst.get)) } match {
      case scala.util.Success(value) => value
      case Failure(exception) => throw exception
    }


  def tweetStream: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

  val route = path("twitter") {
    path("test") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<html><body>Test Route</body></html>"))
      } ~ path("query") {
        post {
          entity(as[JsValue]) { requestJson ⇒
            graphQLEndpoint(requestJson)
          }
        } ~ get {
          logger.info("Landing page")
          getFromResource("./playground.html")
        }
      }
    } ~ path("stream") {
      handleWebSocketMessages(tweetStream)
    }

  } ~ (get & pathEndOrSingleSlash) {
    redirect("twitter/query", StatusCodes.PermanentRedirect)
  }

  def graphQLEndpoint(requestJson: JsValue) = {
    val JsObject(fields) = requestJson

    val JsString(query) = fields("query")

    val operation = fields.get("operationName") collect { case JsString(op) => op }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) => obj
      case _ => JsObject.empty
    }

    QueryParser.parse(query) match {

      // query parsed successfully, time to execute it!
      case scala.util.Success(queryAst) => complete(executeGraphQLQuery(queryAst, operation, vars))

      // can't parse GraphQL query, return error
      case Failure(error) => complete(StatusCodes.BadRequest, JsObject("error" → JsString(error.getMessage)))
    }
  }

  def executeGraphQLQuery(query: Document, operation: Option[String], vars: JsObject) =
    Executor.execute(gqlSchema, query, new QueryContext, variables = vars, operationName = operation)
      .map(StatusCodes.OK -> _)
      .recover {
        case error: QueryAnalysisError => StatusCodes.BadRequest -> error.resolveError
        case error: ErrorWithResolver => StatusCodes.InternalServerError -> error.resolveError
      }


  //Stream in the tweets
  val source = Source.queue[StreamingMessage](100, OverflowStrategy.backpressure)
    .toMat(Sink.foreach {
      case tweet: Tweet =>
        logger.info(s"Tweet: ${tweet.user.get.name} : ${tweet.text}")
        //Publish to EventBus
        TweetWrapper.generateTags(tweet).foreach {
          wrapper => {
            logger.info(s"Tag: ${wrapper.tag}")
            eventBus.publish(wrapper)
          }
        }
    })(Keep.left)
    .run()

  //Twitter
  val client = TwitterStreamingClient()
    .sampleStatuses(tracks = Topics.topics, languages = Seq(Language.English)) {
      case tweet: Tweet => source offer tweet
    }


  // `route` will be implicitly converted to `Flow` using `RouteResult.route2HandlerFlow`
  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  logger.info(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}

