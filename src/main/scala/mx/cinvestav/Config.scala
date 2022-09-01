package mx.cinvestav

case class Config(
                 nodeId:String,
                 port:Int,
                 loadBalancingAlgorithm:String,
                 initNodes:Int,
                 dockerSocket:String,
                 host:String="0.0.0.0",
                 apiVersion:Int = 2,
                 diskCapacity:Long,
                 memoryCapacity:Long,
                 parentNode:String,
                 parentPort:Int,
                 sinkPath:String
                 )
