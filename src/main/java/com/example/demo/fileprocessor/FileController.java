package com.example.demo.fileprocessor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class FileController {

    // TODO: Make configurable
    private static final int convertDocumentInternalPort = 3000;

    // TODO: Consider changing path and input parameter to align with Convert-Document
    @PostMapping("/getPdf")
    public File convertToPojo(@RequestParam(value="payload") MultipartFile payload) {
        String originalFileName = payload.getOriginalFilename();
        File javaIoInputFile = new File(originalFileName);
        File outputFile = null;
        ConvertDocumentDockerDetails convertDocumentDockerDetails = null;
        try {
            javaIoInputFile.createNewFile();
            log.info("Received file: {} of Size: {}", payload.getOriginalFilename(), payload.getSize());
            FileUtils.copyInputStreamToFile(payload.getInputStream(), javaIoInputFile);

            convertDocumentDockerDetails = initializeDockerContainer();
            outputFile = getConvertedFile(javaIoInputFile, convertDocumentDockerDetails);

            log.info("Dispatching converted file: {} of Size: {}", outputFile.getName(), outputFile.length());
            return outputFile;
        }
        catch (IOException | InterruptedException e) {
            log.error("Process terminated to an exception. Converted file was not dispatched.", e);
            return null;
        }
        finally {
            if ( convertDocumentDockerDetails != null && convertDocumentDockerDetails.getDockerClient() != null ) {
                convertDocumentDockerDetails.getDockerClient()
                        .stopContainerCmd(convertDocumentDockerDetails.getDockerId())
                        .exec();
            }
            if ( outputFile != null ) {
                outputFile.deleteOnExit();
            }
            if ( javaIoInputFile != null ) {
                javaIoInputFile.deleteOnExit();
            }
        }
    }

    private ConvertDocumentDockerDetails initializeDockerContainer() throws IOException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        ConvertDocumentDockerDetails convertDocumentDockerDetails = startConvertDocumentContainer(dockerClient);
        dockerClient.startContainerCmd(convertDocumentDockerDetails.getDockerId()).exec();
        int externalPort = getConvertDocumentPort(convertDocumentDockerDetails);
        convertDocumentDockerDetails.setExternalPort(externalPort);
        return convertDocumentDockerDetails;
    }

    private int getConvertDocumentPort(ConvertDocumentDockerDetails convertDocumentDockerDetails){
        DockerClient dockerClient = convertDocumentDockerDetails.getDockerClient();
        String dockerId = convertDocumentDockerDetails.getDockerId();

        Map<Integer, List<String>> boundPorts = Maps.newHashMap();
        dockerClient.inspectContainerCmd(dockerId).exec().getNetworkSettings().getPorts().getBindings()
                .forEach( (exposedPort, bindings) -> {
                    log.info("NETWORK DETAILS: Exposed Port: {}, Binding: {}", exposedPort,
                            bindings);
                    int port = exposedPort.getPort();
                    if ( !boundPorts.containsKey(port)) {
                        boundPorts.put(port, Lists.newArrayList());
                    }
                    for (Ports.Binding binding : bindings) {
                        boundPorts.get(port).add(binding.getHostPortSpec());
                    }
                });
        log.debug("Collective bindings: {}", boundPorts);
        if ( boundPorts.containsKey(convertDocumentInternalPort) && boundPorts.get(convertDocumentInternalPort).size() == 1) {
            return Integer.parseInt(boundPorts.get(convertDocumentInternalPort).get(0));
        }
        throw new IllegalStateException(
                String.format("Unable to find Convert Document Port. Available Port Mappings: %s", boundPorts));
    }

    private ConvertDocumentDockerDetails startConvertDocumentContainer(DockerClient dockerClient){
        ExposedPort exposedPort = new ExposedPort(convertDocumentInternalPort);
        String containerName = "convert-document" + UUID.randomUUID();
        CreateContainerResponse createContainerResponse
                = dockerClient.createContainerCmd("alephdata/convert-document") //TODO: Make this configurable
                .withName(containerName)
                .withExposedPorts(exposedPort)
                .withNetworkMode("bridge")
                .withPublishAllPorts(true)
                .withTty(true).exec();
        String dockerId = createContainerResponse.getId();
        ConvertDocumentDockerDetails convertDocumentDockerDetails = new ConvertDocumentDockerDetails(dockerId,
                containerName, dockerClient);
        return convertDocumentDockerDetails;
    }

    // TODO: Further inspection required to determine cause of "Unexpected end of file from server" caused by
    //  "CRLF expected at end of chunk"
    private File getConvertedFile(File inputFile, ConvertDocumentDockerDetails convertDocumentDockerDetails)
            throws IOException, InterruptedException {
        log.info("Dispatching request to convert document docker container");
        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<String, Object>();
        parts.add("file", inputFile);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<MultiValueMap<String, Object>>(parts, headers);

        // TODO: Make URL configurable
        String url = String.format("http://localhost:%s/convert", convertDocumentDockerDetails.getExternalPort());
        File outputFile = restTemplate.postForObject(url, requestEntity, File.class);
        log.info("Response: {}", outputFile);
        return outputFile;
    }
}
