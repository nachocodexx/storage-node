ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "storage-node",
    libraryDependencies ++= Dependencies(),
    assemblyJarName := "sn.jar",
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      ThisBuild / assemblyMergeStrategy := {
        case x if x.contains("reflect.properties")=> MergeStrategy.last
        case x if x.contains("scala-collection-compat.properties")=> MergeStrategy.last
        case x if x.contains("META-INF/io.netty.versions.properties")=> MergeStrategy.last
        case x if x.contains("META-INF/versions/9/module-info.class")=> MergeStrategy.last
        //
        case x if x.contains("module-info.class")=> MergeStrategy.last
        case x if x.contains("mozilla/public-suffix-list.txt")=> MergeStrategy.last
        case x =>
          val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
          oldStrategy(x)
    }

  )
