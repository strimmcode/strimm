param ([string]$OutputDir="STRIMM.app")

$Files = 
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej/2.0.0-rc-68/imagej-2.0.0-rc-68.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-common/0.26.1/imagej-common-0.26.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2/5.3.0/imglib2-5.3.0.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2-roi/0.5.2/imglib2-roi-0.5.2.jar",
    "$env:USERPROFILE/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar",
    "$env:USERPROFILE/.m2/repository/edu/ucar/udunits/4.3.18/udunits-4.3.18.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-notebook/0.2.3/imagej-notebook-0.2.3.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-ops/0.41.1/imagej-ops-0.41.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-mesh/0.7.1/imagej-mesh-0.7.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2-algorithm/0.9.0/imglib2-algorithm-0.9.0.jar",
    "$env:USERPROFILE/.m2/repository/gov/nist/math/jama/1.0.3/jama-1.0.3.jar",
    "$env:USERPROFILE/.m2/repository/org/ojalgo/ojalgo/45.1.1/ojalgo-45.1.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2-algorithm-fft/0.2.0/imglib2-algorithm-fft-0.2.0.jar",
    "$env:USERPROFILE/.m2/repository/edu/mines/mines-jtk/20151125/mines-jtk-20151125.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2-realtransform/2.0.0/imglib2-realtransform-2.0.0.jar",
    "$env:USERPROFILE/.m2/repository/jitk/jitk-tps/3.0.0/jitk-tps-3.0.0.jar",
    "$env:USERPROFILE/.m2/repository/com/googlecode/efficient-java-matrix-library/ejml/0.24/ejml-0.24.jar",
    "$env:USERPROFILE/.m2/repository/log4j/log4j/1.2.17/log4j-1.2.17.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/parsington/1.0.2/parsington-1.0.2.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-search/0.5.0/scijava-search-0.5.0.jar",
    "$env:USERPROFILE/.m2/repository/org/ocpsoft/prettytime/prettytime/4.0.1.Final/prettytime-4.0.1.Final.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-javascript/0.4.4/scripting-javascript-0.4.4.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-updater/0.9.2/imagej-updater-0.9.2.jar",
    "$env:USERPROFILE/.m2/repository/io/scif/scifio/0.37.0/scifio-0.37.0.jar",
    "$env:USERPROFILE/.m2/repository/io/scif/scifio-jai-imageio/1.1.1/scifio-jai-imageio-1.1.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imglib2/imglib2-cache/1.0.0-beta-9/imglib2-cache-1.0.0-beta-9.jar",
    "$env:USERPROFILE/.m2/repository/com/github/ben-manes/caffeine/caffeine/2.4.0/caffeine-2.4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-common/2.75.0/scijava-common-2.75.0.jar",
    "$env:USERPROFILE/.m2/repository/org/bushe/eventbus/1.4/eventbus-1.4.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-deprecated/0.1.2/imagej-deprecated-0.1.2.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-plugins-commands/0.8.0/imagej-plugins-commands-0.8.0.jar",
    "$env:USERPROFILE/.m2/repository/net/iharder/base64/2.3.8/base64-2.3.8.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-plugins-tools/0.3.1/imagej-plugins-tools-0.3.1.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-plugins-uploader-ssh/0.3.2/imagej-plugins-uploader-ssh-0.3.2.jar",
    "$env:USERPROFILE/.m2/repository/com/jcraft/jsch/0.1.54/jsch-0.1.54.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-plugins-uploader-webdav/0.2.2/imagej-plugins-uploader-webdav-0.2.2.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-scripting/0.8.2/imagej-scripting-0.8.2.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-beanshell/0.3.3/scripting-beanshell-0.3.3.jar",
    "$env:USERPROFILE/.m2/repository/org/apache-extras/beanshell/bsh/2.0b6/bsh-2.0b6.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-clojure/0.1.6/scripting-clojure-0.1.6.jar",
    "$env:USERPROFILE/.m2/repository/org/clojure/clojure/1.8.0/clojure-1.8.0.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-groovy/0.2.7/scripting-groovy-0.2.7.jar",
    "$env:USERPROFILE/.m2/repository/org/codehaus/groovy/groovy/2.4.8/groovy-2.4.8.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/ivy/ivy/2.4.0/ivy-2.4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/codehaus/gpars/gpars/1.2.1/gpars-1.2.1.jar",
    "$env:USERPROFILE/.m2/repository/org/multiverse/multiverse-core/0.7.0/multiverse-core-0.7.0.jar",
    "$env:USERPROFILE/.m2/repository/org/codehaus/jsr166-mirror/jsr166y/1.7.0/jsr166y-1.7.0.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-java/0.4.1/scripting-java-0.4.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/minimaven/2.2.1/minimaven-2.2.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-jruby/0.2.5/scripting-jruby-0.2.5.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/jruby-core/1.7.12/jruby-core-1.7.12.jar",
    "$env:USERPROFILE/.m2/repository/org/ow2/asm/asm/4.0/asm-4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/ow2/asm/asm-commons/4.0/asm-commons-4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/ow2/asm/asm-tree/4.0/asm-tree-4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/ow2/asm/asm-analysis/4.0/asm-analysis-4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/ow2/asm/asm-util/4.0/asm-util-4.0.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/joni/joni/2.1.1/joni-2.1.1.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-netdb/1.1.2/jnr-netdb-1.1.2.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-enxio/0.4/jnr-enxio-0.4.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-x86asm/1.0.2/jnr-x86asm-1.0.2.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-unixsocket/0.3/jnr-unixsocket-0.3.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-posix/3.0.1/jnr-posix-3.0.1.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/extras/bytelist/1.0.11/bytelist-1.0.11.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-constants/0.8.5/jnr-constants-0.8.5.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/jcodings/jcodings/1.0.10/jcodings-1.0.10.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jnr-ffi/1.0.7/jnr-ffi-1.0.7.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jffi/1.2.7/jffi-1.2.7.jar",
    "$env:USERPROFILE/.m2/repository/com/github/jnr/jffi/1.2.7/jffi-1.2.7-native.jar",
    "$env:USERPROFILE/.m2/repository/org/yaml/snakeyaml/1.13/snakeyaml-1.13.jar",
    "$env:USERPROFILE/.m2/repository/com/jcraft/jzlib/1.1.3/jzlib-1.1.3.jar",
    "$env:USERPROFILE/.m2/repository/com/headius/invokebinder/1.2/invokebinder-1.2.jar",
    "$env:USERPROFILE/.m2/repository/com/martiansoftware/nailgun-server/0.9.1/nailgun-server-0.9.1.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/yecht/1.0/yecht-1.0.jar",
    "$env:USERPROFILE/.m2/repository/joda-time/joda-time/2.10/joda-time-2.10.jar",
    "$env:USERPROFILE/.m2/repository/com/headius/options/1.1/options-1.1.jar",
    "$env:USERPROFILE/.m2/repository/org/jruby/jruby-stdlib/1.7.12/jruby-stdlib-1.7.12.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-jython/0.4.2/scripting-jython-0.4.2.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/jython-shaded/2.7.1/jython-shaded-2.7.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-renjin/0.2.2/scripting-renjin-0.2.2.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/renjin-script-engine/0.8.1906/renjin-script-engine-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/renjin-core/0.8.1906/renjin-core-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/commons/commons-math/2.2/commons-math-2.2.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/gcc-runtime/0.8.1906/gcc-runtime-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/netlib/netlib-java/0.9.3-renjin-patched-2/netlib-java-0.9.3-renjin-patched-2.jar",
    "$env:USERPROFILE/.m2/repository/org/netlib/lapack/0.8/lapack-0.8.jar",
    "$env:USERPROFILE/.m2/repository/org/netlib/f2jutil/0.8/f2jutil-0.8.jar",
    "$env:USERPROFILE/.m2/repository/org/netlib/xerbla/0.8/xerbla-0.8.jar",
    "$env:USERPROFILE/.m2/repository/org/netlib/blas/0.8/blas-0.8.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/commons/commons-vfs2/2.0/commons-vfs2-2.0.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/maven/scm/maven-scm-api/1.4/maven-scm-api-1.4.jar",
    "$env:USERPROFILE/.m2/repository/org/codehaus/plexus/plexus-utils/1.5.6/plexus-utils-1.5.6.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/maven/scm/maven-scm-provider-svnexe/1.4/maven-scm-provider-svnexe-1.4.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/maven/scm/maven-scm-provider-svn-commons/1.4/maven-scm-provider-svn-commons-1.4.jar",
    "$env:USERPROFILE/.m2/repository/regexp/regexp/1.3/regexp-1.3.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/commons/commons-compress/1.4.1/commons-compress-1.4.1.jar",
    "$env:USERPROFILE/.m2/repository/org/tukaani/xz/1.0/xz-1.0.jar",
    "$env:USERPROFILE/.m2/repository/edu/emory/mathcs/jtransforms/2.4/jtransforms-2.4.jar",
    "$env:USERPROFILE/.m2/repository/net/sf/jung/jung-api/2.0.1/jung-api-2.0.1.jar",
    "$env:USERPROFILE/.m2/repository/net/sourceforge/collections/collections-generic/4.01/collections-generic-4.01.jar",
    "$env:USERPROFILE/.m2/repository/net/sf/jung/jung-graph-impl/2.0.1/jung-graph-impl-2.0.1.jar",
    "$env:USERPROFILE/.m2/repository/com/sun/codemodel/codemodel/2.6/codemodel-2.6.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/stats/0.8.1906/stats-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/renjin-appl/0.8.1906/renjin-appl-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/renjin-gnur-runtime/0.8.1906/renjin-gnur-runtime-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/methods/0.8.1906/methods-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/datasets/0.8.1906/datasets-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/utils/0.8.1906/utils-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/grDevices/0.8.1906/grDevices-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/renjin/graphics/0.8.1906/graphics-0.8.1906.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scripting-scala/0.2.1/scripting-scala-0.2.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/scala-compiler/2.12.1/scala-compiler-2.12.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/scala-reflect/2.12.1/scala-reflect-2.12.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/modules/scala-xml_2.12/1.0.6/scala-xml_2.12-1.0.6.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-ui-swing/0.21.4/imagej-ui-swing-0.21.4.jar",
    "$env:USERPROFILE/.m2/repository/org/jfree/jfreechart/1.5.0/jfreechart-1.5.0.jar",
    "$env:USERPROFILE/.m2/repository/org/jhotdraw/jhotdraw/7.6.0/jhotdraw-7.6.0.jar",
    "$env:USERPROFILE/.m2/repository/net/imagej/imagej-ui-awt/0.3.1/imagej-ui-awt-0.3.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-plugins-commands/0.2.3/scijava-plugins-commands-0.2.3.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-plugins-platforms/0.3.1/scijava-plugins-platforms-0.3.1.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-plugins-text-markdown/0.1.3/scijava-plugins-text-markdown-0.1.3.jar",
    "$env:USERPROFILE/.m2/repository/org/markdownj/markdownj/0.3.0-1.0.2b4/markdownj-0.3.0-1.0.2b4.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-plugins-text-plain/0.1.3/scijava-plugins-text-plain-0.1.3.jar",
    "$env:USERPROFILE/.m2/repository/org/apache/commons/commons-lang3/3.7/commons-lang3-3.7.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/script-editor/0.2.0/script-editor-0.2.0.jar",
    "$env:USERPROFILE/.m2/repository/com/fifesoft/rsyntaxtextarea/2.6.1/rsyntaxtextarea-2.6.1.jar",
    "$env:USERPROFILE/.m2/repository/com/fifesoft/languagesupport/2.6.0/languagesupport-2.6.0.jar",
    "$env:USERPROFILE/.m2/repository/com/fifesoft/autocomplete/2.6.1/autocomplete-2.6.1.jar",
    "$env:USERPROFILE/.m2/repository/org/mozilla/rhino/1.7.6/rhino-1.7.6.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/batch-processor/0.1.2/batch-processor-0.1.2.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-ui-swing/0.11.0/scijava-ui-swing-0.11.0.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/scijava-ui-awt/0.1.6/scijava-ui-awt-0.1.6.jar",
    "$env:USERPROFILE/.m2/repository/org/scijava/swing-checkbox-tree/1.0.2/swing-checkbox-tree-1.0.2.jar",
    "$env:USERPROFILE/.m2/repository/com/github/sbridges/object-inspector/object-inspector/0.1/object-inspector-0.1.jar",
    "$env:USERPROFILE/.m2/repository/com/miglayout/miglayout/3.7.4/miglayout-3.7.4-swing.jar",
    "$env:USERPROFILE/.m2/repository/net/sourceforge/jdatepicker/jdatepicker/1.3.2/jdatepicker-1.3.2.jar",
    "$env:USERPROFILE/.m2/repository/uk/co/cairnresearch/MMCore/1.0.0/MMCore-1.0.0.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-reflect/1.2.61/kotlin-reflect-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.2.61/kotlin-stdlib-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-common/1.2.61/kotlin-stdlib-common-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/com/google/guava/guava/25.1-jre/guava-25.1-jre.jar",
    "$env:USERPROFILE/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
    "$env:USERPROFILE/.m2/repository/org/checkerframework/checker-qual/2.0.0/checker-qual-2.0.0.jar",
    "$env:USERPROFILE/.m2/repository/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar",
    "$env:USERPROFILE/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar",
    "$env:USERPROFILE/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar",
    "$env:USERPROFILE/.m2/repository/commons-io/commons-io/2.6/commons-io-2.6.jar",
    "$env:USERPROFILE/.m2/repository/org/dockingframes/docking-frames-common/1.1.2-SNAPSHOT/docking-frames-common-1.1.2-20130602.220133-21.jar",
    "$env:USERPROFILE/.m2/repository/org/dockingframes/docking-frames-core/1.1.2-SNAPSHOT/docking-frames-core-1.1.2-20130602.220133-23.jar",
    "$env:USERPROFILE/.m2/repository/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar",
    "$env:USERPROFILE/.m2/repository/commons-beanutils/commons-beanutils/1.9.3/commons-beanutils-1.9.3.jar",
    "$env:USERPROFILE/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar",
    "$env:USERPROFILE/.m2/repository/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar",
    "$env:USERPROFILE/.m2/repository/com/typesafe/akka/akka-actor_2.12/2.5.16/akka-actor_2.12-2.5.16.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/scala-library/2.12.6/scala-library-2.12.6.jar",
    "$env:USERPROFILE/.m2/repository/com/typesafe/config/1.3.3/config-1.3.3.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/modules/scala-java8-compat_2.12/0.8.0/scala-java8-compat_2.12-0.8.0.jar",
    "$env:USERPROFILE/.m2/repository/com/typesafe/akka/akka-stream_2.12/2.5.16/akka-stream_2.12-2.5.16.jar",
    "$env:USERPROFILE/.m2/repository/com/typesafe/akka/akka-protobuf_2.12/2.5.16/akka-protobuf_2.12-2.5.16.jar",
    "$env:USERPROFILE/.m2/repository/org/reactivestreams/reactive-streams/1.0.2/reactive-streams-1.0.2.jar",
    "$env:USERPROFILE/.m2/repository/com/typesafe/ssl-config-core_2.12/0.2.4/ssl-config-core_2.12-0.2.4.jar",
    "$env:USERPROFILE/.m2/repository/org/scala-lang/modules/scala-parser-combinators_2.12/1.1.1/scala-parser-combinators_2.12-1.1.1.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-runtime/1.2.61/kotlin-runtime-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/annotations/13.0/annotations-13.0.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.2.61/kotlin-stdlib-jdk8-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.2.61/kotlin-stdlib-jdk7-1.2.61.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-base/19/javafx-base-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-base/19/javafx-base-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-controls/19/javafx-controls-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-controls/19/javafx-controls-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-fxml/19/javafx-fxml-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-fxml/19/javafx-fxml-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-swing/19/javafx-swing-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-swing/19/javafx-swing-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-web/19/javafx-web-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-web/19/javafx-web-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-media/19/javafx-media-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-media/19/javafx-media-19-win.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-graphics/19/javafx-graphics-19.jar",
    "$env:USERPROFILE/.m2/repository/org/openjfx/javafx-graphics/19/javafx-graphics-19-win.jar",
    "$env:USERPROFILE/.m2/repository/com/opencsv/opencsv/4.0/opencsv-4.0.jar"

function createDir
{
    param([string]$Folder)
    Write-Host "Creating Folder $Folder" -ForegroundColor Cyan
    New-Item -ItemType directory -Path $Folder | Out-Null
}

function copyFile
{
    param([string]$From, [string]$To)
    $Name = [io.path]::GetFileNameWithoutExtension($From)
    Write-Host "Copying $Name to $To" -ForegroundColor Green
    Copy-Item $From -Destination $To
}

function abort
{
    param([string]$Message)
    Write-Host "Error: $Message" -ForegroundColor Red
    exit
}

function getInputFromList
{
    param([string]$Message, [string[]]$Values)
    Write-Host $Message -ForegroundColor Yellow
    $Valid = $false
    $idx = -1
    while (!$Valid){
        Write-Host "Please Select From: " -ForegroundColor Yellow
        $Values | foreach {$i = 0} {
            Write-Host "[$i] $_" -ForegroundColor Yellow
            $i++
        }

        $inp = Read-Host
        $idx = [int]$inp
        if ($idx -ge 0 -and $idx -lt ($Values | Measure-Object).count) {
            $Valid = $true
        }
    }

    $Values[$idx]
}

function copyMavenJar
{
    param([string]$Location, [string]$Name)
    $MavenJar = Get-ChildItem -Path $Location | Where-Object { $_.Name -match "^$Name.\d.\d.\d(-SNAPSHOT)?.jar$" }
    $Count = ($MavenJar | Measure-Object).count
    if (0 -eq $Count) {
        abort "Could not locate $Name jar file! Have you run the maven package?"
    } if (1 -lt $Count) {
        $MavenJar = getInputFromList -Message "Multiple $Name versions found please select the correct one" -Values $MavenJar
    }
    copyFile -From "$Location/$MavenJar" -To "$OutputDir/jars/"
}

function copyFolderContents
{
    param([string]$From, [string]$To)
    Get-ChildItem $From | foreach { copyFile -From "$From/$_" -To $To}
}


if (Test-Path -Path $OutputDir) {
    Write-Host "$OutputDir Already exists. Are you sure you want to proceed? : " -ForegroundColor Red -NoNewline
    $Confirmation = Read-Host

    if ($Confirmation -eq 'y') {
        Remove-Item $OutputDir -Force -Recurse
    }
}

createDir $OutputDir

createDir "$OutputDir/jars"
$Files | foreach { copyFile -From $_ -To "$OutputDir/jars/" }
copyMavenJar -Location "./target" -Name "mainModule"
# copyMavenJar -Location "../STRIMM_JNI/target" -Name "STRIMM_JNI"

#createDir "$OutputDir/DAQs"
#copyFolderContents -From "../WorkingDirectory/DAQs" -To "$OutputDir/DAQs"

createDir "$OutputDir/DeviceAdapters"
copyFolderContents -From "./DeviceAdapters" -To "$OutputDir/DeviceAdapters"

createDir "$OutputDir/luts"
copyFolderContents -From "./luts" -To "$OutputDir/luts"

createDir "$OutputDir/configs"
copyFolderContents -From "./configs" -To "$OutputDir/configs"

Write-Host "Copying loose files" -ForegroundColor Cyan
#copyFile -From "../WorkingDirectory/MMCoreJ_wrap.dll" -To "$OutputDir"
copyFile -From "splash.png" -To "$OutputDir"
copyFile -From "./jarhdf5-3.3.2.jar" -To "$OutputDir/jars"
copyFile -From "./slf4j-api-1.7.5.jar" -To "$OutputDir/jars"
copyFile -From "./slf4j-nop-1.7.5.jar" -To "$OutputDir/jars"
copyFile -From "./slf4j-simple-1.7.5.jar" -To "$OutputDir/jars"
copyFile -From "./*.dll" -To "$OutputDir"
copyFile -From "./*.cfg" -To "$OutputDir"
copyFile -From "./*.jar" -To "$OutputDir"
copyFile -From "./*.json" -To "$OutputDir"
copyFile -From "./*.csv" -To "$OutputDir"
copyFile -From "./*.lib" -To "$OutputDir"
copyFile -From "./STRIMM_settings.txt" -To "$OutputDir"



Write-Host
Write-Host "Packinging Successful" -ForegroundColor Green