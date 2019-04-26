package coop.rchain.rsong.acq.service

import cats.data.State
import com.typesafe.scalalogging.Logger
import coop.rchain.rsong.core.domain._
import coop.rchain.rsong.acq.moc.MocSongMetadata
import coop.rchain.rsong.core.utils.Globals
import coop.rchain.rsong.core.repo.{GRPC, RNodeProxy}
import coop.rchain.rsong.core.repo.RNodeProxyTypeAlias.{ConfigReader, EEString}
import org.specs2._
import org.specs2.specification.BeforeEach
import org.specs2.scalacheck.Parameters
import org.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Gen.{alphaChar, listOfN, posNum}
import org.scalacheck.Prop.forAll

class AcqServiceSpec extends Specification with ScalaCheck with BeforeEach {
  def is =
    s2"""
    AcqService specification are:
      content store, and prefetch spec $p0
  """

  val log = Logger[AcqServiceSpec]
  val contractFile = Globals.appCfg.getString("contract.file.name")
  val server = Server(hostName = "localhost", port = 40401)
  val grpc = GRPC(server)
  val proxy = RNodeProxy()
  val acq = AcqService(proxy)

  val contentGen =
    for {
      id ← Gen.identifier
      data ← Gen.alphaStr
      metadata ← Gen.alphaStr
    } yield
      RsongIngestedAsset(
        id,
        data,
        metadata
      )

  var v: Int = 0

  def before = {
    v = 0

    val work = for {
      _ ← acq.installContract(contractFile)
      p ← acq.proposeBlock
    } yield (p)
    work.run(grpc)
    log.info(s"contract is deployed & proposed.")
  }
  val p0: Prop = Prop.forAll(contentGen)(content ⇒ {
    v = v + 1 //TODO this is bad. FIXME
    val work: ConfigReader[EEString] = for {
      s0 ← acq.store(content)
      _ = log.info(
        s"counter v = ${v} stored content=${content} -- result=${s0}"
      )
      _ ← acq.proposeBlock
      _ = log.info(s"counter v = ${v} prefetching contentid = ${content.id}")
      s1 ← acq.prefetch(content.id)
      _ = log.info(
        s"counter v = ${v} pre-fetched content-id = ${content.id} result= ${s1}"
      )
      s2 ← acq.proposeBlock
    } yield s2
    val computed = work.run(grpc)
    log.info(s"---- computed val is = ${computed}")
    computed.isRight == true
  })
}
