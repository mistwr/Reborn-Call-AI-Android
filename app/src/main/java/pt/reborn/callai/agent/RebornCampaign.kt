package pt.reborn.callai.agent

data class RebornCampaign(
    val id: String,
    val name: String,
    val opening: String,
    val positiveIntentWords: Set<String>,
)

object RebornCampaigns {
    private val brain = RebornConversationBrain()

    val myPouparQualification = RebornCampaign(
        id = "mypoupar_qualification",
        name = "MY POUPar+ · Qualificação",
        opening = brain.opening(),
        positiveIntentWords = setOf(
            "sim", "quero", "aceito", "pode", "claro", "ok", "força", "esta bem", "está bem", "1"
        ),
    )
}
