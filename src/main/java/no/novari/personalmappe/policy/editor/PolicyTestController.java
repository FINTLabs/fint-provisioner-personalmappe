package no.novari.personalmappe.policy.editor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.fint.model.resource.arkiv.personal.PersonalmappeResource;
import no.novari.personalmappe.service.PolicyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/policy/editor")
public class PolicyTestController {

    private final PolicyService policyService;

    public PolicyTestController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public String bladibla(Model model) {
        model.addAttribute("model", new PolicyModel(
                " {\n" +
                        "  \"mappeId\": {\n" +
                        "    \"identifikatorverdi\": \"2020/92\"\n" +
                        "  },\n" +
                        "  \"merknad\": [],\n" +
                        "  \"offentligTittel\": \"Personalmappe - Olsen Ole\",\n" +
                        "  \"opprettetDato\": \"2020-02-04T16:09:10Z\",\n" +
                        "  \"part\": [],\n" +
                        "  \"systemId\": {\n" +
                        "    \"identifikatorverdi\": \"593\"\n" +
                        "  },\n" +
                        "  \"tittel\": \"Personalmappe - Olsen Ole\",\n" +
                        "  \"journalpost\": [],\n" +
                        "  \"saksaar\": \"2020\",\n" +
                        "  \"saksdato\": \"2020-02-03T23:00:00Z\",\n" +
                        "  \"sakssekvensnummer\": \"92\",\n" +
                        "  \"_links\": {\n" +
                        "    \"administrativEnhet\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/arkiv/administrativenhet/systemid/275\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"arkivdel\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/arkiv/arkivdel/systemid/BRAW\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"opprettetAv\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/arkiv/arkivressurs/systemid/1259\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"saksansvarlig\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/arkiv/arkivressurs/systemid/110\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"saksstatus\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/arkiv/saksstatus/systemid/B\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"self\": [\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/personal/personalmappe/mappeid/2020/92\"\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"href\": \"https://alpha.felleskomponent.no/administrasjon/personal/personalmappe/systemid/593\"\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}",
                "function simpleTransformation(o) {\n" +
                        "  return o;\n" +
                        "}",
                null));
        return "policy-editor";
    }

    @PostMapping
    public String evaluate(@ModelAttribute PolicyModel policyModel) {
        try {
            PersonalmappeResource transform = policyService.transform(policyModel.getJs(), new ObjectMapper().readValue(policyModel.getJson(), PersonalmappeResource.class));
            policyModel.setResult(transform);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return "result";
    }
}
