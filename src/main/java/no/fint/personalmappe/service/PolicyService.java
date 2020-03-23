package no.fint.personalmappe.service;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.policy.helper.LinkHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

@Slf4j
@Service
public class PolicyService {

    private ScriptEngine engine;

    @PostConstruct
    public void init() {
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("resource", LinkHelper.resource());
    }

    public PersonalmappeResource transform(String policy, PersonalmappeResource personalmappeResource) {
        try {
            engine.eval(policy);

            log.info("Running transform policy: {}", getFunctionName(policy));
            Object o = ((Invocable) engine).invokeFunction(getFunctionName(policy), personalmappeResource);
            return (PersonalmappeResource) o;
        } catch (ScriptException | NoSuchMethodException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    public String getFunctionName(String policy) throws NoSuchMethodException {
        validateFunctionSignature(policy);
        return policy.substring(policy.indexOf(" "), policy.indexOf("(")).trim();
    }

    public void validateFunctionSignature(String policy) throws NoSuchMethodException {
        if (!StringUtils.startsWith(policy, "function")) {
            throw new NoSuchMethodException("Function signature validation failed");
        }

        if (StringUtils.substringBefore(StringUtils.substringAfter(policy, "("), ")").contains(",")) {
            throw new NoSuchMethodException("Function signature validation failed");
        }
    }
}
