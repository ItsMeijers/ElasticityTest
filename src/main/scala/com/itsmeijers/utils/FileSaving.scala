package com.itsmeijers.utils

import java.io.File
import scala.collection.immutable.Seq
import scala.io.Source.fromFile

trait FileSaving {
  val testFolder = "TestResults"
  val resultFolder = "Results"

  def createFolder(name: String): Boolean = {
    val folder = new File(name)
    if(folder.exists() && folder.isDirectory()) true
    else folder.mkdir()
  }

  def createTestFolder(): Boolean = createFolder(testFolder)

  def createResultFolder(): Boolean = createFolder(resultFolder)

  def newFile(location: String) = new File(s"$testFolder/$location")

  def newHistoryFileJson(resultName: String) =
    new File(s"$resultFolder/$resultName.json")

  def openUpHistoryFiles() =
    new File(s"$resultFolder").listFiles().filter(_.isFile()).toSeq

  def readFile(file: File): String = fromFile(file).mkString

}
