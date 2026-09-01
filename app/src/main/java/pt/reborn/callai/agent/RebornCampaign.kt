package pt.reborn.callai.agent

data class RebornCampaign(
    val id: String,
    val name: String,
    val opening: String,
    val positiveIntentWords: Set<String>,
)

object RebornCampaigns {
    val myPouparQualification = RebornCampaign(
        id = "mypoupar_qualification",
        name = "MY POUPar+ · Qualificação",
        opening = "Olá, boa tarde. Sou o assistente de inteligência artificial da MY POUPar+. Estamos a contactar para ajudar a verificar se os seus serviços de energia ou telecomunicações continuam atualizados e se existe uma solução mais adequada para si. Se quiser que um gestor confirme gratuitamente as opções disponíveis, diga sim ou marque 1.",
        positiveIntentWords = setOf("sim", "quero", "aceito", "pode", "claro", "ok", "um", "1"),
    )
}
