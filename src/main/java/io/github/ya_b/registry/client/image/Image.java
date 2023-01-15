package io.github.ya_b.registry.client.image;

import io.github.ya_b.registry.client.blob.Blob;
import io.github.ya_b.registry.client.name.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Image {

    private Tag tag;

    private Blob config;

    private List<Blob> layers;

    Image(Image image) {
        setTag(image.getTag());
        setConfig(image.getConfig());
        setLayers(image.getLayers());
    }

}
