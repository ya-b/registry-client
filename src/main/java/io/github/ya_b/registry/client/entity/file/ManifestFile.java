package io.github.ya_b.registry.client.entity.file;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class ManifestFile {

    @SerializedName("Config")
    private String config;
    @SerializedName("RepoTags")
    private List<String> repoTags;
    @SerializedName("Layers")
    private List<String> layers;
}
