package mx.cinvestav.routes
import mx.cinvestav.Declarations.{Ball, NodeContext, NodeId, NodeInfo, NodeState, NodeX, ReplicationProcess}
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


  def pushTo(where:List[NodeX]) (implicit ctx:NodeContext)= for {
    _  <- IO.unit
    _ <- ctx.logger.debug(s"PUSH TO ${where.map(_.id).mkString(",")}")
  } yield ()

  def writePush(rp:ReplicationProcess)(implicit ctx:NodeContext,state:Ref[IO,NodeState]) = for {
    currentState <- state.get
    balls        = currentState.balls
    children     = currentState.children
    childrenIds  = children.map(_.id)
    where        = children.filter(x=>rp.where.contains(x.id))
    whereElastic = rp.where.filterNot(w=> childrenIds.contains(w)).map(_.id).map(NodeId.fromStr)
    _ <- ctx.logger.debug("ELASTIC: "+whereElastic.asJson.spaces4)
//  _______________________________________________________________________
    _            <- rp.what.traverse{ b=>
      for {
        _      <- IO.unit
        exists = balls.keys.toList.contains(b.ballId)
        _      <- if(exists) pushTo(where)
        else for {
          downloadStartTime <- IO.realTime.map(_.toMillis)
          readReq           = Request[IO](method = Method.GET, uri = Uri.unsafeFromString(b.url))
          path              = Path(s"${ctx.config.sinkPath}/${b.ballId}")
           _                <- ctx.client.stream(req = readReq).flatMap(_.body).through(Files[IO].writeAll(path = path)).compile.drain
          downloadST        <- IO.realTime.map(_.toMillis - downloadStartTime)
          _                 <- ctx.logger.debug(s"DOWNLOAD ${b.ballId} $downloadST")
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

  def read(ballId:String)(implicit ctx:NodeContext) = for {
    _        <- IO.unit
    headers  = Headers.empty
    status   = Status.Ok
    writeResponseData   = WriteResponse(operationId = "op-0", serviceTime = 1L)
    ent      = Entity(
      body = Stream.emits(writeResponseData.asJson.noSpaces.getBytes).covary[IO],
      length = None
    )
    response = Response[IO](
      status = status,
      headers = headers,
      entity = ent
    )
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
    arrivalTime <- IO.realTime.map(_.toMillis)
    nodeId       = ctx.config.nodeId
    diskCapacity = ctx.config.diskCapacity
    memCap       = ctx.config.memoryCapacity
    currentState <- state.get
    balls        = currentState.balls
    children     = currentState.children
    nodeInfo     = NodeInfo(
      id             = nodeId,
      diskCapacity   = diskCapacity,
      memoryCapacity = memCap,
      balls          = balls.values.toList,
      children       = children
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