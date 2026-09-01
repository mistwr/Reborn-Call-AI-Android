package pt.reborn.callai.agent

import java.text.Normalizer

enum class LeadTemperature { COLD, WARM, HOT, CALLBACK }

enum class CustomerIntent {
    POSITIVE,
    NEGATIVE,
    CALLBACK,
    PRICE,
    CURRENT_PROVIDER,
    ENERGY,
    TELECOM,
    QUESTION,
    UNKNOWN,
}

data class ConversationTurn(
    val reply: String,
    val intent: CustomerIntent,
    val temperature: LeadTemperature,
    val shouldCreateLead: Boolean = false,
    val shouldScheduleCallback: Boolean = false,
    val shouldStop: Boolean = false,
)

/**
 * Fast local conversation controller used before/alongside the LLM.
 * It keeps the call natural, short and safe: one idea/question per turn,
 * PT-PT phrasing, explicit AI disclosure and no claims about a customer's
 * bill/service unless those facts were actually provided.
 */
class RebornConversationBrain {

    fun opening(): String =
        "Olá, boa tarde. Sou a assistente virtual da MY POUPar+. É uma chamada rápida para ajudar a perceber se os seus serviços de energia ou telecomunicações continuam competitivos. Posso explicar em vinte segundos?"

    fun onCustomerText(raw: String, previousTemperature: LeadTemperature = LeadTemperature.COLD): ConversationTurn {
        val text = normalize(raw)

        if (text.isBlank()) {
            return ConversationTurn(
                reply = "Estou a ouvir. Pode dizer-me só se faz sentido verificarmos isso consigo?",
                intent = CustomerIntent.UNKNOWN,
                temperature = previousTemperature,
            )
        }

        if (containsAny(text, "nao quero", "não quero", "sem interesse", "nao estou interessado", "não estou interessado", "retire", "nao liguem", "não liguem")) {
            return ConversationTurn(
                reply = "Claro. Obrigada pelo seu tempo e não o incomodo mais. Boa continuação.",
                intent = CustomerIntent.NEGATIVE,
                temperature = LeadTemperature.COLD,
                shouldStop = true,
            )
        }

        if (containsAny(text, "agora nao", "agora não", "mais tarde", "depois", "amanha", "amanhã", "ligue mais tarde", "outro dia")) {
            return ConversationTurn(
                reply = "Sem problema. Prefere que um gestor lhe ligue ainda hoje ou noutro dia?",
                intent = CustomerIntent.CALLBACK,
                temperature = LeadTemperature.CALLBACK,
                shouldCreateLead = true,
                shouldScheduleCallback = true,
            )
        }

        if (containsAny(text, "quanto custa", "preco", "preço", "valor", "quanto pago", "quanto fica")) {
            return ConversationTurn(
                reply = "Consigo ajudar a comparar, mas o valor certo depende do que tem hoje. Quer começar pela energia ou pela internet de casa?",
                intent = CustomerIntent.PRICE,
                temperature = LeadTemperature.WARM,
            )
        }

        if (containsAny(text, "meo", "nos", "vodafone", "digi", "nowo", "uzo", "woo", "amigo")) {
            return ConversationTurn(
                reply = "Perfeito. Então já sei qual é a sua operadora. O que pesa mais para si neste momento: o preço mensal ou a qualidade da internet?",
                intent = CustomerIntent.CURRENT_PROVIDER,
                temperature = LeadTemperature.WARM,
            )
        }

        if (containsAny(text, "energia", "luz", "eletricidade", "fatura da luz")) {
            return ConversationTurn(
                reply = "Certo. Na energia a verificação é simples e não o obriga a mudar. Quer que um gestor compare a sua situação atual sem custo?",
                intent = CustomerIntent.ENERGY,
                temperature = LeadTemperature.WARM,
            )
        }

        if (containsAny(text, "internet", "fibra", "televisao", "televisão", "telemovel", "telemóvel", "wifi")) {
            return ConversationTurn(
                reply = "Percebi. Na telecomunicações podemos comparar preço, cobertura e o que realmente usa. Quer que façamos essa verificação consigo?",
                intent = CustomerIntent.TELECOM,
                temperature = LeadTemperature.WARM,
            )
        }

        if (containsAny(text, "sim", "pode", "quero", "claro", "ok", "está bem", "esta bem", "aceito", "forca", "força")) {
            val hot = previousTemperature == LeadTemperature.WARM || containsAny(text, "quero", "pode ligar", "contactem", "contacte", "compare", "comparar")
            return if (hot) {
                ConversationTurn(
                    reply = "Perfeito. Vou deixar o pedido preparado para um gestor confirmar consigo as melhores opções. Qual é a melhor altura para falar?",
                    intent = CustomerIntent.POSITIVE,
                    temperature = LeadTemperature.HOT,
                    shouldCreateLead = true,
                )
            } else {
                ConversationTurn(
                    reply = "Obrigado. A ideia é só confirmar se o que tem hoje continua competitivo. Preocupa-se mais com o valor mensal ou com a qualidade do serviço?",
                    intent = CustomerIntent.POSITIVE,
                    temperature = LeadTemperature.WARM,
                )
            }
        }

        if (text.endsWith("?") || containsAny(text, "como", "porquê", "porque", "quem", "o que", "qual")) {
            return ConversationTurn(
                reply = "Claro. Posso esclarecer. Para não lhe dar informação genérica, diga-me só se a dúvida é sobre energia ou telecomunicações.",
                intent = CustomerIntent.QUESTION,
                temperature = maxTemperature(previousTemperature, LeadTemperature.WARM),
            )
        }

        return ConversationTurn(
            reply = "Percebi. Para eu não complicar: quer que verifiquemos primeiro energia ou telecomunicações?",
            intent = CustomerIntent.UNKNOWN,
            temperature = maxTemperature(previousTemperature, LeadTemperature.WARM),
        )
    }

    fun systemPrompt(): String = """
        És a voz da REBORN AI para a MY POUPar+, em português de Portugal.
        Fala como uma pessoa profissional e calorosa, nunca como um robô de call center.
        Regras obrigatórias:
        - identifica-te como assistente virtual/IA no início;
        - usa frases curtas e naturais, normalmente uma ou duas frases por turno;
        - faz apenas uma pergunta de cada vez;
        - não inventes preços, falhas, poupanças, coberturas ou dados do cliente;
        - não digas que detetámos problemas numa fatura/serviço sem dados que o provem;
        - aceita interrupções e responde ao que a pessoa acabou de dizer;
        - se houver desinteresse claro, termina educadamente;
        - se pedir outro horário, cria CALLBACK;
        - ouvir apenas = WARM; intenção concreta de comparar/ser contactado = HOT;
        - quando HOT, recolhe apenas o mínimo necessário e encaminha para humano/CRM;
        - nunca presses o cliente para fornecer dados desnecessários.
    """.trimIndent()

    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "")
    }

    private fun containsAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(normalize(it)) }

    private fun maxTemperature(a: LeadTemperature, b: LeadTemperature): LeadTemperature {
        val rank = mapOf(
            LeadTemperature.COLD to 0,
            LeadTemperature.WARM to 1,
            LeadTemperature.CALLBACK to 2,
            LeadTemperature.HOT to 3,
        )
        return if ((rank[a] ?: 0) >= (rank[b] ?: 0)) a else b
    }
}
