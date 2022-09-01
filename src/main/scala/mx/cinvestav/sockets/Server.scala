package mx.cinvestav.sockets
import cats.implicits._
import cats.effect._
import fs2.io.net.Network
import fs2.Stream
import fs2.io.file.Files
import com.comcast.ip4s.{Host, Port}
import mx.cinvestav.Declarations.NodeContext

object Server {
  def apply(address:Option[Host],port:Option[Port]) (implicit ctx:NodeContext)={
    Network[IO].server(address = address, port = port).map{ socket=>
      val stream  = socket.reads
//      val headers = stream.take()
      Stream.empty.covary[IO]
        .evalMap((x:Nothing)=>ctx.logger.debug("RECEIVED..."))
        .handleErrorWith{
          e=>
            Stream.eval(ctx.logger.error(e.getMessage))
        }
    }.parJoin(100)
  }
}
