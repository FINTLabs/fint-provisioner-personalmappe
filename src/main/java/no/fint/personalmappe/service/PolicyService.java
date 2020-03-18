package no.fint.personalmappe.service;

import no.fint.model.resource.administrasjon.personal.PersonalmappeResource;
import no.fint.personalmappe.policy.helper.LinkHelper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

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

            Object o = ((Invocable) engine).invokeFunction(getFunctionName(policy), personalmappeResource);
            return (PersonalmappeResource) o;
        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getFunctionName(String policy) {
        return policy.substring(policy.indexOf(" "), policy.indexOf("(")).trim();
    }
}
