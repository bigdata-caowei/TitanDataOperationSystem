package cn.edu.neu.titan.titanSpark.migration


import org.apache.spark.sql.SaveMode

/**
 * Created by IntelliJ IDEA.
 *
 * @Author: 张志浩 Zhang Zhihao
 * @Email: 3382885270@qq.com
 * @Date: 2020/7/20 
 * @Time: 9:05
 * @Version: 1.0
 * @Description: Description 
 */
package object participation {

  /**
   * 张志浩需要，改写王阔得
   * 源表及对应列名，目标表，维度等信息执行操作
   *
   * @param maps     源表及对应列名
   * @param tbTarget 目标表
   * @param tbDim    维度表
   * @param dimName  维度名
   */
  def executeMigrate2(dt: String, maps: Array[(String, String)], tbTarget: String, tbDim: String, dimName: String, colTargets: Array[String]): Unit = {

    // 临时变量
    val tbDimTemp = s"tb${dimName}Temp"
    val tbBaseTemps = new Array[String](maps.length)
    val tbPrefix = s"${dimName}Temp"

    createTableDim(tbDim, tbDimTemp, dimName)

    var i = 0
    maps.foreach(t => {
      val tbBaseTemp = tbPrefix + i
      createTableBase(dt, t, tbBaseTemp, dimName)
      tbBaseTemps.update(i, tbBaseTemp)
      i += 1
    })

    joinAndInsert2(tbBaseTemps, tbTarget, dimName, tbDimTemp, colTargets)
  }

  /**
   * 获取表中相应维度的数据并创建临时视图
   *
   * @param map        (源表名,代表数量的列名)
   * @param tbTempName 临时表名
   * @param dimName    维度名
   */
  def createTableBase(dt: String, map: (String, String), tbTempName: String, dimName: String): Unit = {
    val sql_create = s"select $dimName, " +
      "case when version is NULL then '' else version end as version," +
      "case when channel is NULL then '' else channel end as channel," +
      s"${map._2} num from ${map._1} " +
      s"where dt='$dt' and $dimName is Not NULL"

    // 创建临时视图
    spark.sql(sql_create).createOrReplaceTempView(tbTempName)
  }

  /**
   * 创建维度表用于补零
   *
   * @param tbDimName  维度表
   * @param tbTempName 创建的临时表的名字
   */
  def createTableDim(tbDimName: String, tbTempName: String, dimName: String): Unit = {

    val dimMysql = s"Mysql$dimName"
    spark.read.jdbc(url, tbDimName, connectionProperties).createOrReplaceTempView(dimMysql)

    val sql_create = s"select * from $dimMysql, $tbCVJoin "
    spark.sql(sql_create).createOrReplaceTempView(tbTempName)
  }

  /**
   * 张志浩需要，改写王阔得
   * 做左外连接并插入结果
   *
   * @param tbBaseNames 临时表
   * @param tbTarget    目标表
   * @param dimName     维度名
   * @param tbDimName   维度表
   */
  def joinAndInsert2(tbBaseNames: Array[String], tbTarget: String, dimName: String, tbDimName: String, colTargets: Array[String]): Unit = {

    val cols_part = StringBuilder.newBuilder
    val join_part = StringBuilder.newBuilder

    var i = 0
    while (i < tbBaseNames.length) {
      val elem = tbBaseNames(i)
      val col = colTargets(i)
      cols_part ++= s", case when $elem.num is NULL then 0 else $elem.num end as $col "
      //      cols_part ++= s", $elem.num as $col "
      join_part ++= s"left join $elem on name=$elem.$dimName and vname=$elem.version and cname=$elem.channel "
      i += 1
    }

    val dateName = tbTarget.split("_")(2)

    val sql_insert = s"select '$currentDate' ${dateName}_date, " +
      "cid channel_id," +
      "vid version_id," +
      "id range_id" +
      s"$cols_part " +
      s"from $tbDimName $join_part "

    //    println(sql_insert)
    spark.sql(sql_insert).write.mode(SaveMode.Append).jdbc(url, tbTarget, connectionProperties)
  }
}
