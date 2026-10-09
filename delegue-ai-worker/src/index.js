export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowed = origin === "https://emasse44-crypto.github.io" || origin === "http://localhost:8787";
    const cors = {
      "Access-Control-Allow-Origin": allowed ? origin : "null",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Vary": "Origin"
    };

    if (request.method === "OPTIONS") return new Response(null, {status:204, headers:cors});
    if (request.method !== "POST") return json({error:"Méthode non autorisée."},405,cors);

    const url = new URL(request.url);
    if (url.pathname !== "/scan") return json({error:"Endpoint inconnu."},404,cors);
    if (!env.OPENAI_API_KEY) return json({error:"Clé OpenAI non configurée sur le Worker."},500,cors);

    try {
      const body = await request.json();
      const image = body && body.image;
      const licenseImage = body && body.licenseImage;
      if (typeof image !== "string" || !image.startsWith("data:image/")) {
        return json({error:"Image manquante ou format invalide."},400,cors);
      }
      if (image.length > 12_000_000) return json({error:"Photo trop volumineuse. Reprenez une photo nette, sans zoom excessif."},413,cors);
      if (typeof licenseImage === "string" && licenseImage.length > 12_000_000) return json({error:"Photo recadrée trop volumineuse."},413,cors);

      const schema = {
        type:"object",
        additionalProperties:false,
        properties:{
          home:{type:"object",additionalProperties:false,properties:{
            players:{type:"array",items:{type:"object",additionalProperties:false,properties:{
              number:{type:"integer",minimum:1,maximum:16},name:{type:"string"},license:{type:"string"}
            },required:["number","name","license"]}},
            staff:{type:"array",maxItems:5,items:{type:"object",additionalProperties:false,properties:{
              name:{type:"string"},license:{type:"string"},role:{type:"string",enum:["E","M","A","D","D/DR",""]}
            },required:["name","license","role"]}}
          },required:["players","staff"]},
          away:{type:"object",additionalProperties:false,properties:{
            players:{type:"array",items:{type:"object",additionalProperties:false,properties:{
              number:{type:"integer",minimum:1,maximum:16},name:{type:"string"},license:{type:"string"}
            },required:["number","name","license"]}},
            staff:{type:"array",maxItems:5,items:{type:"object",additionalProperties:false,properties:{
              name:{type:"string"},license:{type:"string"},role:{type:"string",enum:["E","M","A","D","D/DR",""]}
            },required:["name","license","role"]}}
          },required:["players","staff"]}
        },
        required:["home","away"]
      };

      const prompt = [
        "PRIORITÉ ABSOLUE : lis les numéros de licence dans la colonne située immédiatement à droite du nom de chaque joueur.",
"Examine attentivement chaque ligne et distingue le nom du joueur de son numéro de licence. Ne confonds jamais le numéro de maillot avec la licence.",
"Recopie chaque numéro de licence exactement, chiffre par chiffre, pour les titulaires, les remplaçants et les membres du staff.",
"Si un numéro est partiellement difficile à lire, examine attentivement les chiffres visibles. Ne complète jamais les chiffres manquants par supposition.",
"Ne laisse une licence vide que si elle est absente ou réellement illisible sur la photo."
        "Si aucune licence n’est imprimée ou si elle est réellement illisible, renvoie license comme chaîne vide. N’invente jamais de chiffres.",
        "Ne mélange JAMAIS les joueurs des deux équipes.",
        "Les lignes sous les joueurs correspondent au staff. Recopie le nom et le rôle quand il est visible: E, M, A, D ou D/DR.",
        "Ignore les titres, en-têtes, arbitres et autres personnes qui ne sont pas joueurs ou staff.",
        "Si un nom est illisible, renvoie une chaîne vide plutôt que d'inventer.",
        "Le résultat doit être strictement conforme au schéma JSON."
      ].join(" ");

      const ai = await fetch("https://api.openai.com/v1/responses", {
        method:"POST",
        headers:{
          "Authorization":"Bearer "+env.OPENAI_API_KEY,
          "Content-Type":"application/json"
        },
        body:JSON.stringify({
          model:"gpt-5.4-mini",
          input:[{
            role:"user",
            content:[
              {type:"input_text",text:prompt},
              {type:"input_image",image_url:image,detail:"high"},
              ...(typeof licenseImage === "string" && licenseImage.startsWith("data:image/") ? [{type:"input_image",image_url:licenseImage,detail:"high"}] : [])
            ]
          }],
          text:{format:{type:"json_schema",name:"football_match_sheet",strict:true,schema:schema}},
          max_output_tokens:3000
        })
      });

      const aiText = await ai.text();
      if (!ai.ok) {
        return json({error:"OpenAI a refusé ou échoué l’analyse.",details:aiText.slice(0,1000)},502,cors);
      }
      const response = JSON.parse(aiText);
      const output = response.output || [];
      let textOut="";
      for (const item of output) {
        if (item.type === "message" && Array.isArray(item.content)) {
          for (const part of item.content) if (part.type === "output_text") textOut += part.text || "";
        }
      }
      if (!textOut) return json({error:"Aucun résultat exploitable retourné par l’IA."},502,cors);
      return json(JSON.parse(textOut),200,cors);
    } catch (e) {
      return json({error:e && e.message ? e.message : "Erreur interne du Worker."},500,cors);
    }
  }
};

function json(data,status,headers){
  return new Response(JSON.stringify(data),{
    status,
    headers:Object.assign({"Content-Type":"application/json; charset=utf-8"},headers||{})
  });
}
