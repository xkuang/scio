/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.util

import java.net.URI

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.services.dataflow.Dataflow
import com.google.api.services.dataflow.model.JobMetrics
import com.google.cloud.dataflow.sdk.coders.{Coder, CoderRegistry}
import com.google.cloud.dataflow.sdk.options.{DataflowPipelineDebugOptions,
                                              DataflowPipelineOptions,
                                              PipelineOptions}
import com.google.cloud.dataflow.sdk.runners.DirectPipelineRunner
import com.google.cloud.dataflow.sdk.runners.inprocess.InProcessPipelineRunner
import com.google.cloud.dataflow.sdk.util.BigQueryTableRowIterator

import scala.reflect.ClassTag

private[scio] object ScioUtil {

  def isLocalUri(uri: URI): Boolean = uri.getScheme == null || uri.getScheme == "file"

  def isGcsUri(uri: URI): Boolean = uri.getScheme == "gs"

  def classOf[T: ClassTag]: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

  def getScalaCoder[T: ClassTag]: Coder[T] = {
    import com.spotify.scio.Implicits._

    val coderRegistry = new CoderRegistry()
    coderRegistry.registerStandardCoders()
    coderRegistry.registerScalaCoders()

    coderRegistry.getScalaCoder[T]
  }

  def isLocalRunner(options: PipelineOptions): Boolean = {
    val runner = options.getRunner

    require(runner != null, "Pipeline runner not set!")

    runner.isAssignableFrom(classOf[DirectPipelineRunner]) ||
      runner.isAssignableFrom(classOf[InProcessPipelineRunner])
  }

  def getScalaJsonMapper: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
  }

  def getDataflowServiceClient(options: PipelineOptions): Dataflow =
    options.as(classOf[DataflowPipelineDebugOptions]).getDataflowClient

  def executeWithBackOff[T](request: AbstractGoogleClientRequest[T], errorMsg: String): T = {
    // Reuse util method from BigQuery
    BigQueryTableRowIterator.executeWithBackOff(request, errorMsg)
  }

  def getDataflowServiceMetrics(options: DataflowPipelineOptions, jobId: String): JobMetrics = {
    val getMetrics = ScioUtil.getDataflowServiceClient(options)
      .projects()
      .jobs()
      .getMetrics(options.getProject, jobId)

    ScioUtil.executeWithBackOff(getMetrics,
      s"Could not get dataflow metrics of ${getMetrics.getJobId} in ${getMetrics.getProjectId}")
  }


}