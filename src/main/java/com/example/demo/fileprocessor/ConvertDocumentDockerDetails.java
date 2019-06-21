package com.example.demo.fileprocessor;

import com.github.dockerjava.api.DockerClient;
import lombok.*;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ConvertDocumentDockerDetails {
    private final String dockerId;
    private final String containerName;
    @Setter
    private int externalPort;
    private final DockerClient dockerClient;
}
