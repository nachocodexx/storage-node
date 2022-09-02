package mx.cinvestav

import cats.effect.{ExitCode, IO, IOApp, Ref}
import cats.implicits._
import retry.RetryPolicies
//
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
//
import mx.cinvestav.Declarations.{NodeContext, NodeState}
import mx.cinvestav.docker.DockerAPI
import mx.cinvestav.helpers.Communication
//
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.blaze.client.BlazeClientBuilder
//
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger
//
import pureconfig._
import pureconfig.generic.auto._
//
import retry.syntax._
import retry.implicits._
//
import scala.concurrent.duration._
import language.postfixOps._
///
import fs2.Stream
//
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._


object Main extends IOApp{

  implicit val config: Config = ConfigSource.default.loadOrThrow[Config]
  implicit val unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName[IO]("default")
  val unsafeErrorLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromName("error")




  override def run(args: List[String]): IO[ExitCode] = {
    val program = for{
      _                  <- Logger[IO].debug("REPLICA_MANAGER v0.0.1")
//   _______________________________________________________________
      dockerClientConfig = new DefaultDockerClientConfig.Builder()
        .withDockerHost(config.dockerSocket)
        .withDockerTlsVerify(false)
        .build();
      _                  <- Logger[IO].debug(s"DOCKER_HOST ${dockerClientConfig.getDockerHost}")
      dockerHttpClient   = new ApacheDockerHttpClient.Builder()
        .dockerHost(dockerClientConfig.getDockerHost)
        .build()
//_______________________________________________________________
      (client,clientFinalizer)<- BlazeClientBuilder[IO].resource.allocated

      implicit0(ctx:NodeContext) <- NodeContext(
        config    = config,
        logger    = unsafeLogger,
        dockerApi = new DockerAPI(config = dockerClientConfig, httpClient = dockerHttpClient),
        client    = client
      ).pure[IO]
      _            <- Communication.parentHandshaking()
        .retryingOnFailures(
          wasSuccessful   =  response=>IO.pure(response.status.compare(Status.NoContent) == 0),
          policy          = RetryPolicies.limitRetries[IO](maxRetries = 100) join RetryPolicies.exponentialBackoff(baseDelay = 200.seconds),
          onFailure       =  (res,rd)=> {
            ctx.logger.debug("HANDSHAKING_RETRY_DETAILS"+rd.toString) *> ctx.logger.debug(s"ON_FAILURE_RESPONSE $res")
          },
        )
        .start
      initialState  = NodeState(children = Nil, usedDiskCapacity = config.diskCapacity)
      implicit0(state:Ref[IO,NodeState]) <- IO.ref(initialState)
      _            <- ctx.logger.debug(s"SERVER ON PORT ${config.port}")

      _ <- Stream.awakeEvery[IO](period = 5.seconds)
        .evalMap{ _ =>
          for {
            currentState <- state.get
            children     = currentState.children
            childrenIds  = children.map(_.id)
            pendings     = currentState.pendingReplications
            _            <- pendings.traverse{ p=>
               for {
                  _      <- IO.unit
                  _      <- if(childrenIds.contains(p.who)) {
                    for {
                      _ <- IO.unit
                      uri    = Uri.unsafeFromString(s"http://${p.who}:${ctx.config.port}/api/v${ctx.config.apiVersion}/write")
                      entity = Entity(
                        body = Stream.emits((p::Nil).asJson.noSpaces.getBytes)
                      )
                      req = Request[IO](
                        method = Method.POST,
                        uri = uri,
                        entity = entity
                      )
                      _            <- ctx.client.stream(req = req)
                        .evalTap { response =>
                          if (response.status.compare(Status.Ok) == 0) state.update(s => s.copy(pendingReplications = s.pendingReplications.filter(x => x != p)))
                          else IO.unit
                        }
                        .evalTap{
                          response => ctx.logger.debug("TRY_PENDING_RESPONSE "+response.toString)
                        }
                        .compile.drain
                    } yield ()
                  }
                  else IO.unit
               } yield ()
            }.handleError{ e=>
              ctx.logger.debug(e.getMessage)
            }
          } yield ()
        }
        .compile.drain.start
      exitCode     <- Server()
        .serve
        .compile
        .lastOrError.onError{ e=>clientFinalizer}
      _ <- clientFinalizer
    } yield exitCode

    program.onError{ e=>
      Logger[IO].error(e.getMessage)
    }
  }
}
