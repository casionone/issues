/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.linkis.ecm.restful;

import org.apache.linkis.common.utils.JsonUtils;
import org.apache.linkis.ecm.server.exception.ECMErrorException;
import org.apache.linkis.server.utils.ModuleUserUtils;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.linkis.ecm.errorcode.EngineconnServerErrorCodeSummary.FILE_IS_OVERSIZE;
import static org.apache.linkis.ecm.errorcode.EngineconnServerErrorCodeSummary.LOG_IS_NOT_EXISTS;

@Api(tags = "ECM")
@RequestMapping(path = "/engineconnManager")
@RestController
public class ECMRestfulApi {

  private final Logger logger = LoggerFactory.getLogger(ECMRestfulApi.class);

  @RequestMapping(path = "/downloadEngineLog", method = RequestMethod.POST)
  public void downloadEngineLog(
      HttpServletRequest req, HttpServletResponse response, @RequestBody JsonNode jsonNode)
      throws IOException, ECMErrorException {
    ModuleUserUtils.getOperationUser(req, "downloadEngineLog");
    String instance = jsonNode.get("instance").asText();
    Map<String, Object> parameters = new HashMap<>();
    try {
      parameters =
          JsonUtils.jackson()
              .readValue(
                  jsonNode.get("parameters").toString(),
                  new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      logger.error(
          "Fail to process the operation parameters: [{}] in request",
          jsonNode.get("parameters").toString(),
          e);
    }
    String logType = (String) parameters.get("logType");
    String logDirSuffix = (String) parameters.get("logDirSuffix");
    File inputFile = new File(logDirSuffix, logType);
    if (!inputFile.exists()) {
      throw new ECMErrorException(
          LOG_IS_NOT_EXISTS.getErrorCode(),
          MessageFormat.format(LOG_IS_NOT_EXISTS.getErrorDesc(), logDirSuffix));
    } else {
      long fileSizeInBytes = inputFile.length();
      long fileSizeInMegabytes = fileSizeInBytes / (1024 * 1024);
      if (fileSizeInMegabytes > 100) {
        throw new ECMErrorException(
            FILE_IS_OVERSIZE.getErrorCode(),
            MessageFormat.format(FILE_IS_OVERSIZE.getErrorDesc(), logDirSuffix));
      }
      ServletOutputStream outputStream = null;
      FileInputStream inputStream = null;
      BufferedInputStream fis = null;
      PrintWriter writer = null;
      try {
        inputStream = new FileInputStream(inputFile);
        fis = new BufferedInputStream(inputStream);
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        response.reset();
        response.setCharacterEncoding(Consts.UTF_8.toString());
        java.nio.file.Path source = Paths.get(inputFile.getPath());
        response.addHeader("Content-Type", Files.probeContentType(source));
        response.addHeader(
            "Content-Disposition",
            "attachment;filename=" + instance.replace(":", "_") + "_" + logType + ".txt");
        outputStream = response.getOutputStream();
        while ((bytesRead = fis.read(buffer, 0, 1024)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      } catch (IOException e) {
        logger.error("download failed", e);
        response.reset();
        response.setCharacterEncoding(Consts.UTF_8.toString());
        response.setContentType("text/plain; charset=utf-8");
        writer = response.getWriter();
        writer.append("error(错误):" + e.getMessage());
        writer.flush();
      } finally {
        if (outputStream != null) {
          outputStream.flush();
        }
        IOUtils.closeQuietly(outputStream);
        IOUtils.closeQuietly(fis);
        IOUtils.closeQuietly(inputStream);
      }
    }
  }
}
