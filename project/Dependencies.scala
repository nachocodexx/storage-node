import sbt._

object Dependencies {

  def apply(): Seq[ModuleID] = {

    lazy val CatsEffects = "org.typelevel" %% "cats-effect" % "3.3.14"

    val http4sVersion = "1.0.0-M35"

    lazy val Http4s =Seq(
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-blaze-server" ,
      "org.http4s" %% "http4s-blaze-client",
      "org.http4s" %% "http4s-circe"
    ).map(_ % http4sVersion)
    lazy val Logback   = "ch.qos.logback" % "logback-classic" % "1.2.11"
    lazy val CatsNIO = "io.github.akiomik" %% "cats-nio-file" % "1.7.0"
    lazy val PureConfig = "com.github.pureconfig" %% "pureconfig" % "0.17.1"
    lazy val MUnitCats ="org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    lazy val Log4Cats =   "org.typelevel" %% "log4cats-slf4j"   % "2.4.0"
    val catsRetryVersion = "3.1.0"
    lazy val CatsRetry = "com.github.cb372" %% "cats-retry" % catsRetryVersion


    lazy val DockerClient = "com.github.docker-java" % "docker-java" % "3.2.13"
    lazy val DockerTransportHttpClient5 = "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.13"
    val circeVersion = "0.15.0-M1"
    lazy val Circe = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)

    val fs2Version = "3.2.12"
    lazy val Fs2 = Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version
    )

    lazy val Ip4s ="com.comcast" %% "ip4s-core" % "3.1.3"
    Seq(
      CatsEffects,
      CatsNIO,
      CatsRetry,
      PureConfig,
      MUnitCats,
      Log4Cats,
      Logback,
      DockerClient,
      DockerTransportHttpClient5,
      Ip4s
    ) ++ Http4s ++ Circe ++ Fs2

  }
}
