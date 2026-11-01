package com.homemedguard.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审计日志里绝不能出现口令、密钥和病历原文。
 * 已加密的值（enc:v1: 开头）也一律替换，避免审计表成为第二个可读副本。
 */
public final class AuditPayloadSanitizer {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordhash", "refreshtoken", "accesstoken", "token",
            "jwtsecret", "datakey", "allergyhistory", "chronicconditions",
            "allergies", "data-key", "jwt-secret");

    private AuditPayloadSanitizer() {
    }

    public static String toJson(ObjectMapper objectMapper, Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(payload);
            mask(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "{\"unserializable\":true}";
        }
    }

    private static void mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (SENSITIVE_KEYS.contains(key)) {
                    field.setValue(object.objectNode().put("masked", MASK));
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(AuditPayloadSanitizer::mask);
        }
    }
}
