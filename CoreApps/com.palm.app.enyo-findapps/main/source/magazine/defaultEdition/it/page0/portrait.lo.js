[
    {kind: "VFlexBox", className: "portrait", style: "background:#1a1a2e;height:1024px;width:768px;",
     pack: "center", align: "center", components: [
        {kind: "VFlexBox", style: "position:relative;top:-50px;", align: "center", components: [
            {kind: "Spinner", showing: true, style: "margin-bottom:12px;"},
            {kind: "ProgressBar", className: "pivot-progress-bar", position: 0, style: "width:280px;margin-bottom:8px;"},
            {content: "0%", className: "pivot-progress-label", style: "color:#fff;font-size:13px;margin-bottom:20px;"},
            {kind: "Control", content: "Download dell'ultimo numero di Pivot in corso...",
             style: "color:#fff;font-size:20px;text-align:center;padding:0 40px;margin-bottom:30px;"},
            {content: "Visualizza app recenti", className: "pivot-recent-apps-button", target: "recentapps", onclick: "goToTargetAction",
             style: "color:#fff;font-size:16px;text-align:center;padding:10px 24px;border:1px solid #fff;border-radius:4px;"}
        ]}
    ]}
]
