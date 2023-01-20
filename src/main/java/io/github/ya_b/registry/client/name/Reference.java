package io.github.ya_b.registry.client.name;

import io.github.ya_b.registry.client.constant.Constants;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class Reference {
    private static final int DIGEST_LENGTH = 71;

    private String endpoint;
    private String name;
    private String tag;
    private String digest;

    public static Reference parse(String image) {
        Reference t = new Reference();
        List<String> list = new ArrayList<>(Arrays.asList(image.split(Constants.SEPARATOR)));
        if (list.size() > 1 && (list.get(0).contains(Constants.COLON) || list.get(0).contains(Constants.DOT))) {
            t.endpoint = list.remove(0);
        } else {
            t.endpoint = Constants.ENDPOINT_DEFAULT;
        }
        String last = list.get(list.size() - 1);
        if (last.contains(Constants.AT)) {
            int atIndex = last.lastIndexOf(Constants.AT);
            t.digest = last.substring(atIndex + 1);
            if (t.digest.length() != DIGEST_LENGTH || !t.digest.contains(Constants.SHA256_PREFIX)) {
                throw new IllegalArgumentException("digest format error");
            }
            last = last.substring(0, atIndex);
        }
        if (last.contains(Constants.COLON)) {
            int colonIndex = last.indexOf(Constants.COLON);
            t.tag = last.substring(colonIndex + 1);
            last = last.substring(0, colonIndex);
        } else {
            t.tag = Constants.TAG_LATEST;
        }
        list.set(list.size() - 1, last);
        if (Constants.ENDPOINT_DEFAULT.equals(t.endpoint) && list.size() == 1) {
            list.add(0, Constants.GROUP_DEFAULT);
        }
        t.name = String.join(Constants.SEPARATOR, list);
        return t;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (endpoint != null && !endpoint.isEmpty()) {
            stringBuilder.append(endpoint);
            stringBuilder.append(Constants.SEPARATOR);
        }
        if (name != null && !name.isEmpty()) {
            stringBuilder.append(name);
            stringBuilder.append(Constants.COLON);
        }
        if (tag != null && !tag.isEmpty()) {
            stringBuilder.append(tag);
        } else {
            stringBuilder.append(Constants.TAG_LATEST);
        }
        return stringBuilder.toString();
    }
}
