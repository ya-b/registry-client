package io.github.ya_b.registry.client.http.resp;

import lombok.Data;

import java.util.List;

import com.google.cloud.tools.jib.json.JsonTemplate;

@Data
public class CatalogResp implements JsonTemplate {

    private List<String> repositories;

    private String next;
}
