package mx.cinvestav.routes
import mx.cinvestav.Declarations.{Ball, Docker, NodeContext, NodeId, NodeInfo, NodeState, NodeX, ReplicationProcess, WhereEntry}
import mx.cinvestav.docker.Spawner

import scala.util.Random
//
import cats.implicits._
import cats.effect._
//
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci.CIString
import org.http4s.circe._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
//
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
//
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
//
//import cats.nio.file.{Files=>NioFiles}
//import java.nio.file.Paths

object Index {

  case class WriteResponse(operationId:String,serviceTime:Long)


  def pushTo(rp:ReplicationProcess,where:List[NodeX]) (implicit ctx:NodeContext)= for {
    _  <- IO.unit
    _  <- where.traverse{ w=>
      for {
         _          <- IO.unit
         id         = w.id
         port       = w.port
         apiVersion = ctx.config.apiVersion
         uri        = Uri.unsafeFromString(s"http://$id:$port/api/v$apiVersion/write")
         newRp      = rp.copy(where = Nil)
         entity     = Entity(body =Stream.emits((newRp::Nil).asJson.noSpaces.getBytes))
         req        = Request[IO](method = Method.POST,uri = uri,entity = entity)
         _          <- ctx.client.stream(req = req)
           .evalTap(response => ctx.logger.debug(s"PUSH_RESPONSE $response"))
           .compile.drain
      } yield ()
    }
//    _ <- ctx.logger.debug(s"PUSH TO ${where.map(_.id).mkString(",")}")
  } yield ()

  def writePush(rp:ReplicationProcess)(implicit ctx:NodeContext,state:Ref[IO,NodeState]) = for {
    currentState <- state.get
    sinkPath     = Path(ctx.config.sinkPath)
    nodeId       = ctx.config.nodeId
    nodePort     = ctx.config.port
    apiVersion   = ctx.config.apiVersion
    balls        = currentState.balls
    children     = currentState.children
    childrenIds  = children.map(_.id)
    rpWheresIds  = rp.where.map(_.id)
    where        = children.filter(x=>rpWheresIds.contains(x.id))
    whereElastic = rp.where.filterNot(w=> childrenIds.contains(w.id))
    _            <- ctx.logger.debug("ELASTIC: "+whereElastic.asJson.spaces4)
    xs           <- if(rp.elastic)
      whereElastic.traverse {
        w =>
          val port = w.metadata.getOrElse("PORT", Random.between(10000,25000).toString)
          val portInt = port.toInt
          println(s"PORT $port")

          Spawner.createNode(
            nodeId = w.id,
            ports  = Docker.Ports( host = portInt, docker= nodePort),
            environments = Map(
              "NODE_ID" -> w.id,
              "NODE_PORT" -> nodePort.toString,
              "SINK_PATH" -> "/sink",
              "PARENT_NODE" -> nodeId,
              "PARENT_PORT" -> nodePort.toString,
              "LOG_PATH" -> "/logs"
            ),
            volumes = Map(
              s"/test/sink/${w.id}"->"/sink",
              s"/test/logs/${w.id}" -> "/logs",
              "/var/run/docker.sock" -> "/var/run/docker.sock"
            ),
            resources = Docker.Resources.empty
          ) *> state.update(s=>s.copy(
            pendingReplications =  s.pendingReplications :+ rp.copy(
              who = w.id,
              what = rp.what.map{ w =>
                val url  = s"http://$nodeId:$nodePort/api/v$apiVersion/read/${w.ballId}"
                w.copy(url = url)
              },
              where = Nil
            )
          ))
      }.onError{ e=>
        ctx.logger.error(e.getMessage)
      }
    else IO.pure(Nil)
//  _______________________________________________________________________
    _            <- rp.what.traverse{ b=>
      for {
        _      <- IO.unit
        exists = balls.keys.toList.contains(b.ballId)
        _      <- if(exists) pushTo(rp,where )
        else for {
          downloadStartTime <- IO.realTime.map(_.toMillis)
          readReq           = Request[IO](method = Method.GET, uri = Uri.unsafeFromString(b.url))
          path              = sinkPath/b.ballId
           _                <- ctx.client.stream(req = readReq).flatMap(_.body).through(Files[IO].writeAll(path = path)).compile.drain
          downloadST        <- IO.realTime.map(_.toMillis - downloadStartTime)
          _                 <- ctx.logger.debug(s"DOWNLOAD $nodeId ${b.ballId} $downloadST")
          _                 <- pushTo(rp,where)
        } yield ()

      } yield ()
    }
//   _______________________________________________________________________
  } yield ()
//
  def write(req:Request[IO])(implicit ctx:NodeContext,state:Ref[IO,NodeState]) = for {
    arrivalTime          <- IO.realTime.map(_.toMillis)
    replicationProcesses <- req.as[List[ReplicationProcess]]
    _                    <- ctx.logger.debug(replicationProcesses.toString)
    _                    <- replicationProcesses.traverse{ rp=>
      for {
        _ <- IO.unit
        _ <- if(rp.how.transferType == "PUSH") writePush(rp)
        else IO.unit
      } yield ()
    }
//    _______________________________________________________________
    headers              = Headers.empty
    status               = Status.Ok
    writeResponseData    = WriteResponse(operationId = "op-0", serviceTime = 1L)
    bytes                = writeResponseData.asJson.noSpaces.getBytes
//  __________________________________________________________________________
    ent                  = Entity(
      body   = Stream.emits(bytes).covary[IO],
      length = bytes.length.toLong.some
    )
//  __________________________________________________________________________
    response             = Response[IO](
      status = status,
      headers = headers,
      entity = ent
    )
//  __________________________________________________________________________
  } yield response

  def read(ballId:String)(implicit ctx:NodeContext,state:Ref[IO,NodeState]) = for {
    arrivalTime <- IO.realTime.map(_.toMillis)
    sinkPathStr = ctx.config.sinkPath
    sinkPath    = Path(sinkPathStr)
    ballPath    = sinkPath / ballId
    exists      <- Files[IO].exists(ballPath)
    response    <- if(exists) for {
      _        <- IO.unit
      ballSize <- Files[IO].size(ballPath)
      headers  = Headers(
        Header.Raw(CIString("Ball-Size"),ballSize.toString )
      )
      status   = Status.Ok
      ent      = Entity(
        body   = Files[IO].readAll(ballPath),
        length = None
      )
      response = Response[IO](
        status = status,
        headers = headers,
        entity = ent
      )
    } yield response
    else Response(status = Status.NotFound).pure[IO]
  } yield response

  def completeOperation(operationId:String)(implicit ctx:NodeContext) = for {
    _ <- IO.unit
    response = Response[IO](
      status  = Status.Ok,
      headers =  Headers.empty,
      entity  =  Entity.empty
    )
  } yield response

  def handshake(nodeId: String,req:Request[IO])(implicit ctx:NodeContext,state:Ref[IO,NodeState]) = for {
    arrivalTime <- IO.realTime.map(_.toMillis)
    _           <- ctx.logger.debug(s"HANDSHAKE $nodeId")
    headers     = req.headers
    port        = headers.get(CIString("Node-Port")).flatMap(_.head.value.toIntOption).getOrElse(0)
    node        = NodeX(id = nodeId,port = port)
    _           <- state.update(s=>s.copy(children = s.children :+ node ))
    response    = Response[IO](
      status  = Status.NoContent,
      headers =  Headers.empty,
      entity  =  Entity.empty
    )
  } yield response

  def info(implicit ctx:NodeContext,state:Ref[IO,NodeState]): IO[Response[IO]] = for {
    arrivalTime         <- IO.realTime.map(_.toMillis)
    nodeId              = ctx.config.nodeId
    diskCapacity        = ctx.config.diskCapacity
    memCap              = ctx.config.memoryCapacity
    currentState        <- state.get
    balls               = currentState.balls
    children            = currentState.children
    pendingReplications = currentState.pendingReplications
    nodeInfo            = NodeInfo(
      id                  = nodeId,
      diskCapacity        = diskCapacity,
      memoryCapacity      = memCap,
      balls               = balls.values.toList,
      children            = children,
      pendingReplications = pendingReplications
    )
    entity       = Entity(body = Stream.emits(nodeInfo.asJson.noSpaces.getBytes))
    response     = Response[IO](
      status  = Status.Ok,
      headers = Headers.empty,
      entity  = entity
    )
  } yield response

  def apply()(implicit ctx:NodeContext,state:Ref[IO,NodeState]): HttpRoutes[IO] =  HttpRoutes.of[IO]{
    case req@GET -> Root                               => info
    case req@POST -> Root / "write"                    => write(req)
    case req@GET  -> Root / "read" / ballId            => read(ballId = ballId)
    case req@POST -> Root / "completed" / operationId  => completeOperation(operationId = operationId)
    case req@POST -> Root / "handshake" / nodeId       => handshake(nodeId,req)
  }

}