package mx.cinvestav.docker
import cats.implicits._
import cats.effect._
import com.github.dockerjava.api.model.{Bind, Binds, ExposedPort, HostConfig, Ports, Volume}
import mx.cinvestav.Declarations.{Docker, NodeContext}

object Spawner {
  def createNode(
                  nodeId:String= "",
                  ports:Docker.Ports,
                  image:String,
                  network:Docker.Network,
                  environments:Map[String,String],
                  ipcMode:String="",
                  volumes:Map[String,String]=Map.empty[String,String],
                  labels:Map[String,String]=Map.empty[String,String],
                  externalNetwork:Boolean = true,
                  resources:Docker.Resources
                )(implicit ctx:NodeContext) =
  {
    for {
      _                <- IO.unit
      exportedPorts    = ExposedPort.tcp(ports.docker)
      bindPorts        = if(ports.host===0) Ports.Binding.empty() else Ports.Binding.bindPort(ports.host)
      ports2           = new Ports(exportedPorts,bindPorts)
      containerName    = Docker.Name(nodeId)
      dockerImage      = Docker.Image.fromString(image)
      hostname         = Docker.Hostname(nodeId)
      //
      createNetwork = for {
        networkRes <- ctx.dockerApi.createNetwork(network = network)
        _ <- ctx.logger.debug(networkRes.toString)
      } yield ()


      _ <- if(externalNetwork) IO.unit else createNetwork
      //      _________________________________________________
      _binds           = volumes.map{
        case (hostBind, dockerBind) =>
          val vol = new Volume(dockerBind)
          new Bind(hostBind,vol)
      }.toList
      binds = new Binds(_binds:_*)
      //      _________________________________________________
      hostConfig     = new HostConfig()
        .withPortBindings(ports2)
        .withNetworkMode(network.name)
        .withBinds(binds)
        .withIpcMode(ipcMode)
        .withCpuCount(resources.cpuCount)
        .withCpuQuota(resources.cpuQuota)
        .withCpuPeriod(resources.cpuPeriod)
        .withMemory(resources.memory)
      //      __________________________________________________
      containerId <- ctx.dockerApi
        .createContainer(name=containerName, image=dockerImage,
          hostname=hostname,
          envs=Docker.Envs(environments),
          hostConfig = hostConfig,
          labels = labels
        )
        .map(_.withExposedPorts(exportedPorts).exec()).map(_.getId)
        .onError(e=>ctx.logger.error(e.getMessage))
      //      _________________________________________________
      _ <- ctx.dockerApi.startContainer(containerId).map(_.exec()).onError{e =>
        ctx.logger.error(s"START_CONTAINER_ERROR ${e.getMessage}") *> ctx.dockerApi.deleteContainer(containerId).map(_.exec()).void
      }
    } yield containerId}
}
