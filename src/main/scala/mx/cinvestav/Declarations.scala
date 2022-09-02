package mx.cinvestav

import cats.effect.IO
import com.github.dockerjava.api.model.HostConfig
import mx.cinvestav.docker.DockerAPI
import org.http4s.client.Client
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.annotation.tailrec

object Declarations {

    case class NodeId(parent:String,children:List[NodeId] = Nil)
    object NodeId {
//        def fromStrWithParent(parent:String, re)
        def empty: NodeId = NodeId(parent = "",children = Nil)
        def fromStr(nodeId:String): NodeId ={
            val xs         = nodeId.split('_')
            val xsReversed = xs.reverse.toList

            @tailrec
            def inner(xsR:List[String], nIds:List[NodeId]):List[NodeId]= {
                if(xsR.isEmpty)  nIds
                else if(xsR.length == 1)
                    nIds :+ NodeId(parent = xsR.head, children = nIds)
                else {
                    inner(xsR.tail, nIds = nIds :+ NodeId(parent = xsR.head , children = nIds  ))
                }
            }
            if(xsReversed.length ==1)
                NodeId(parent = xs.head,children = Nil)
            else {
                val children = inner(xsReversed.init, Nil)
                NodeId(parent = xs.head,children = children)
            }
        }
    }
    case class Ball(id:String,size:Long,createdAt:Long,metadata:Map[String,String])
    case class NodeInfo(
                       id:String,
                       diskCapacity:Long,
                       memoryCapacity:Long,
                       balls:List[Ball]=Nil,
                       children:List[NodeX],
                       pendingReplications:List[ReplicationProcess]
                       )
    case class NodeX(id:String, port:Int)

    case class NodeState(
                          children:List[NodeX] = Nil,
                          usedDiskCapacity:Long = 0L,
                          balls:Map[String,Ball]= Map.empty[String,Ball],
                          pendingReplications:List[ReplicationProcess] = Nil
                        )
    case class NodeContext(
                            config:Config,
                            logger:Logger[IO],
                            dockerApi:DockerAPI,
                            parent:Option[String] = None,
                            client:Client[IO]
                          )


    case class What(ballId:String,url:String,metadata:Map[String,String])
    case class How(replicationTechnique:String,transferType:String,deferred:Boolean = false)
    case class WhereEntry(id:String,metadata:Map[String,String]=Map.empty[String,String])
    case class When(timing:String,metadata:Map[String,String])
//    case object Reactive extends When
//    case class Proactive(metadata:Map[String,String])

    case class ReplicationProcess(
                                 who:String="",
                                 what:List[What],
                                 where:List[WhereEntry],
                                 how:How,
                                 when:When,
                                 elastic:Boolean = false
                                 )


    object Docker {
        case class Ports(host:Int,docker:Int)
        object Ports {
            def empty = Ports(host = 0,docker =0)
        }
        case class CreateContainerData(name:Name,image:Image,hostname: Hostname,envs:Envs,hostConfig: HostConfig)
        //
        case class Network(name:String,driver:String = "bridge",labels:Map[String,String]= Map.empty[String,String],attachable:Boolean = true)
        object Network {
            def empty = Network(name = "test")
        }
        //
        case class Envs(values:Map[String,String]){
            def build:Array[String] = {
                values.toArray.map{
                    case (key, value) => s"$key=$value"
                }
            }
        }
        case class Hostname(value:String){
            def build:String = value
        }
        case class Name(value:String){
            def build:String = value
        }
        case class Image(repository:String,tag:Option[String]= Some("latest")){
            def build:String = tag match {
                case Some(tag) => s"$repository:$tag"
                case None => s"$repository:latest"
            }
        }
        object Image {
            def fromString(x:String) = {
                val xx   = x.split(':')
                val name = xx(0)
                if(xx.length == 1) Image(name,None)
                else{
                    val tag  = xx.lastOption
                    Image(name,tag)
                }
            }
        }

        case class Resources (
                               cpuCount:Long=1L,
                               cpuPeriod:Long = 0L,
                               cpuQuota:Long = 0L   ,
                               memory:Long = 1000000000,
                             )
        object Resources {
            def empty = Resources()
        }
    }
}
