package com.itsmeijers.models

package object ChartModels {
  // For representing Column required in googlechart
  case class Column(
    id: String,
    `type`: String,
    label: Option[String],
    role: Option[String])

  // For representing Row required in googlechart
  case class Row(c: Seq[RowValue])

  sealed trait RowValue
  case class StringValue(v: String) extends RowValue
  case class NumberValue(v: Double) extends RowValue

  // For representing chart data
  case class Data(cols: Seq[Column], rows: Seq[Row])

  case class UriData(cols: Seq[Column], rows: Seq[Row], uri: String)

  case class GraphData(
    responseInstance: Data,
    statusBar: Data,
    statusPie: Data)
    // lineChars: Seq[Data] -> for each uri

}
