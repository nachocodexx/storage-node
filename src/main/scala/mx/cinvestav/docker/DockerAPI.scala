package mx.cinvestav.docker
import cats.implicits._
import cats.effect._
import com.github.dockerjava.api.command.{CreateServiceResponse, ListContainersCmd, StartContainerCmd}
import com.github.dockerjava.api.model.{ContainerPort, ContainerSpec, ExposedPort, HostConfig, Mount, MountType, NetworkAttachmentConfig, Ports, ServiceSpec, TaskSpec}
import com.github.dockerjava.core.{DockerClientConfig, DockerClientImpl}
import com.github.dockerjava.transport.DockerHttpClient
import mx.cinvestav.Declarations.Docker

import scala.jdk.CollectionConverters._

class DockerAPI(config: DockerClientConfig,httpClient: DockerHttpClient) {
  private val client = DockerClientImpl.getInstance(config, httpClient)



  def getIpAddress(containerId:String,networkName:String): IO[Option[String]] = IO.delay{
    val x= client.listContainersCmd()
      .withNameFilter(
        List(containerId).asJava
      ).exec()
      .asScala
      .toList
    x.map(_.getNetworkSettings.getNetworks.get(networkName).getIpAddress)
      .headOption
  }

  def remove(containerId:String): IO[Void] = IO.delay{
    client.removeContainerCmd(containerId).exec()
  }

  def stop(containerId:String) = IO.delay{
    client.stopContainerCmd(containerId).exec()
  }

  def getPorts(containerId:String): IO[Option[List[ContainerPort]]] = IO.delay{
    client.listContainersCmd()
      .withIdFilter(
        List(containerId).asJava
      ).exec().asScala.toList
      .map{ x=>
        x.getPorts
      }.headOption.map(x=>x.toList)
  }
  def getPortListByNodeId(containerName:String) =
    getPortByNodeId(containerName = containerName)
      .map(_.map(_.map(_.getPublicPort).distinct))



  def getPortByNodeId(containerName:String)= IO.delay{
    client.listContainersCmd()
      .withNameFilter(
        List(containerName).asJava
      )
      .exec().asScala.toList
      .map{ x=>
        x.getPorts
      }.headOption.map(x=>x.toList)
  }

  def getServiceById(serviceId:String) = {
    IO.delay{
      client.listServicesCmd()
        .withIdFilter(
          (serviceId::Nil).asJava
        )
    }
  }


  def getContainerById(containerId:String): IO[ListContainersCmd] = IO.delay{
    client.listContainersCmd()
      .withIdFilter(
        (containerId::Nil).asJava
      )
  }

  def startContainer(containerId:String): IO[StartContainerCmd] = IO.delay{
    client.startContainerCmd(containerId)
  }

  def deleteContainer(containerId:String) = {
    IO.delay{
      client.removeContainerCmd(containerId)
    }
  }


  def deleteService(serviceId:String): IO[Unit] = {
    IO.defer{
      client.removeServiceCmd(serviceId).exec().pure[IO]
    }.void
  }

  def createService(
                     name:Docker.Name,
                     image: Docker.Image,
                     labels:Map[String,String],
                     envs: Docker.Envs,
                     networkName:String="my-net",
                     hostLogPath:String="/home/jcastillo/logs",
                     dockerLogPath:String="/app/logs",
                   ): IO[CreateServiceResponse] = {
    //    val networks    = List((new NetworkAttachmentConfig()).with).asJava
    val logMount       = (new Mount())
      .withType(MountType.BIND)
      .withSource(hostLogPath)
      .withTarget(dockerLogPath)

    val containerSpec = (new ContainerSpec())
      .withEnv(envs.build.toList.asJava)
      .withHostname(name.build)
      .withImage(image.build)
      .withLabels(labels.asJava)
      .withMounts(List(logMount).asJava)


    val myNetAttach = (new NetworkAttachmentConfig())
      .withTarget(networkName)

    val taskSpec = (new TaskSpec())
      .withContainerSpec(containerSpec)
      .withNetworks(List(myNetAttach).asJava)




    val serviceSpec = (new ServiceSpec())
      .withName(name.build)
      .withLabels(labels.asJava)
      .withTaskTemplate(taskSpec)
      .withNetworks(List(myNetAttach).asJava)

    IO.delay{
      client.createServiceCmd(serviceSpec).exec()
    }
  }

  def createNetwork(network: Docker.Network) =
    for {
      x <- IO.delay {
        client.createNetworkCmd()
          .withName(network.name)
          .withLabels(network.labels.asJava)
          .withDriver(network.driver)
          .withAttachable(network.attachable)
          .exec()
      }

    } yield x

  def createContainer(name:Docker.Name,
                      image:Docker.Image,
                      hostname: Docker.Hostname,
                      envs:Docker.Envs,
                      hostConfig: HostConfig,
                      labels:Map[String,String] = Map.empty[String,String]
                     ) = {
    IO.pure(
      client.createContainerCmd(image.build)
        .withName(name.build)
        .withHostName(hostname.build)
        .withEnv(envs.build: _*)
        .withHostConfig(hostConfig)
        .withLabels(labels.asJava)

      //         .with
    )
  }

//  def createContainerV2(x: CreateContainerData) = {
//    IO.pure(
//      client.createContainerCmd(x.image.build)
//        .withName(x.name.build)
//        .withHostName(x.hostname.build)
//        .withEnv(x.envs.build: _*)
//        .withHostConfig(x.hostConfig)
//    )
//  }

}
object DockerAPI {

  def apply(config: DockerClientConfig,httpClient: DockerHttpClient) =
    new DockerAPI(config,httpClient)

}
