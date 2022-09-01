package mx.cinvestav

import cats.effect._
import mx.cinvestav.Declarations.{NodeContext, NodeState}
import mx.cinvestav.routes.Index
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router


object Server {
  def httpApp()(implicit ctx:NodeContext,state:Ref[IO,NodeState]) =
    Router(
      s"/api/v${ctx.config.apiVersion}" -> Index()
    ).orNotFound
//    (Index()).orNotFound

  def apply()(implicit ctx: NodeContext,state:Ref[IO,NodeState]): BlazeServerBuilder[IO] = {
    BlazeServerBuilder[IO]
      .withHttpApp(httpApp = httpApp())
      .bindHttp(port = ctx.config.port,host = ctx.config.host)

  }
}
