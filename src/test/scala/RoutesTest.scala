import cats.implicits._
import cats.effect._
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import mx.cinvestav.Config
import mx.cinvestav.Declarations.{NodeContext, NodeId, NodeState}
import mx.cinvestav.docker.DockerAPI
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.typelevel.log4cats.{Logger, LoggerName, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger
//
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import mx.cinvestav.routes.Index
import pureconfig._
import pureconfig.generic.auto._
import mx.cinvestav.Declarations
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._

class RoutesTest extends munit .CatsEffectSuite {
  implicit val config: Config = ConfigSource.default.loadOrThrow[Config]
  implicit val unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName[IO]("default")

  def initContext(client:Client[IO]): IO[NodeContext] = for {
    _       <- IO.unit
    dockerClientConfig = new DefaultDockerClientConfig.Builder()
      .withDockerHost(config.dockerSocket)
      .withDockerTlsVerify(false)
      .build();
    _                  <- Logger[IO].debug(s"DOCKER_HOST ${dockerClientConfig.getDockerHost}")
    dockerHttpClient   = new ApacheDockerHttpClient.Builder()
      .dockerHost(dockerClientConfig.getDockerHost)
      .build()
    //_______________________________________________________________
    dockerAPI = DockerAPI(config = dockerClientConfig,httpClient = dockerHttpClient)
    context = Declarations.NodeContext(
      config = config,
      logger = unsafeLogger,
      dockerApi = dockerAPI,
      parent = None,
      client = client
    )
  } yield context


  test("NodeId") {
//    val nId0 = NodeId.fromStr("sn0_sn1_sn2")
//    println(nId0.children.last)
  }

  test("Metadata from Array of Bytes") {
    case class Metadata(id:String,size:Long)
    val m0          = Metadata(id = "b0",size = 1L)
    val m0Json      = m0.asJson
    val m0Bytes     = m0Json.noSpaces.getBytes
    val m0BytesSize = m0Bytes.length

//    val headers =
  }


  test("Test") {
    for {
       _                          <- IO.unit
       (client,clientFinalizer)   <- BlazeClientBuilder[IO].resource.allocated
       implicit0(ctx:NodeContext) <- initContext(client=client)
       initState                   = NodeState()
       implicit0(state:Ref[IO,NodeState]) <- IO.ref(initState)
       getRouteRequest            = Request[IO](Method.GET,uri"/")
       serviceIO                  = Index().orNotFound.run(getRouteRequest)
       _                          <- serviceIO.flatTap(x=>ctx.logger.debug(x.toString()))
      _                           <- clientFinalizer
    } yield ()
  }

}
