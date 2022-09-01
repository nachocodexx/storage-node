package mx.cinvestav.helpers

import cats.implicits._
import cats.effect._
import mx.cinvestav.Declarations.NodeContext
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci.CIString

object Communication {
  def parentHandshaking()(implicit ctx:NodeContext): IO[Response[IO]] = for {
    _           <- IO.unit
    parentNode  = ctx.config.parentNode
    response           <- if(parentNode.nonEmpty) {
       for {
         _           <- IO.unit
         parentPort  = ctx.config.parentPort
         apiVersion  = ctx.config.apiVersion
         childNodeId = ctx.config.nodeId
         uri         = Uri.unsafeFromString(s"http://$parentNode:$parentPort/api/v$apiVersion/handshake/$childNodeId")
         headers     = Headers(
           Header.Raw(name = CIString("Node-Port"),value = ctx.config.port.toString)
         )
         req         = Request[IO](method = Method.POST, uri = uri,headers =  headers)
         _ <- ctx.logger.debug(req.toString)
//         status      <- ctx.client.status(req = req)
         response    <- ctx.client.stream(req = req).compile.lastOrError
         _           <- ctx.logger.debug(s"PARENT_HANDSHAKING_STATUS $response")
       } yield response
    }
    else Response(status = Status.NoContent).pure[IO]
  } yield response

}
