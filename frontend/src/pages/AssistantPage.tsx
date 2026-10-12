import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { assistantApi } from "@/api";
import type { ChatMessage } from "@/api/types";
import { PageHeader } from "@/components/ui/states";
import { Button, Badge, Spinner } from "@/components/ui/primitives";
import { cn } from "@/lib/cn";

const SUGGESTIONS = [
  "Why did my environmental score change?",
  "What is causing most of my emissions?",
  "How can I reduce my carbon footprint?",
  "Which month had the highest emissions?",
  "What should I prioritize?",
  "Give me a summary of my performance.",
];

interface LocalMsg extends Partial<ChatMessage> { role: "USER" | "ASSISTANT"; content: string; pending?: boolean; disclaimers?: string[]; grounded?: boolean; model?: string; }

export default function AssistantPage() {
  const qc = useQueryClient();
  const [conversationId, setConversationId] = useState<string | undefined>();
  const [draft, setDraft] = useState("");
  const [thread, setThread] = useState<LocalMsg[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  const convs = useQuery({ queryKey: ["ai-conversations"], queryFn: () => assistantApi.conversations() });

  useEffect(() => {
    if (!conversationId) return;
    assistantApi.conversation(conversationId).then((c) => {
      setThread(c.messages.filter((m) => m.role !== "SYSTEM").map((m) => ({ role: m.role as "USER" | "ASSISTANT", content: m.content, model: m.model ?? undefined })));
    });
  }, [conversationId]);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [thread]);

  const send = useMutation({
    mutationFn: (message: string) => assistantApi.chat(message, conversationId),
    onMutate: (message) => {
      setThread((t) => [...t, { role: "USER", content: message }, { role: "ASSISTANT", content: "", pending: true }]);
    },
    onSuccess: (res) => {
      setConversationId(res.conversationId);
      setThread((t) => {
        const copy = [...t];
        copy[copy.length - 1] = { role: "ASSISTANT", content: res.reply, disclaimers: res.disclaimers, grounded: res.groundingCheckPassed, model: res.model };
        return copy;
      });
      qc.invalidateQueries({ queryKey: ["ai-conversations"] });
    },
    onError: (e: any) => {
      setThread((t) => {
        const copy = [...t];
        copy[copy.length - 1] = { role: "ASSISTANT", content: e?.code === "LLM_UNAVAILABLE"
          ? "The AI assistant isn't configured. Set IEIA_LLM_PROVIDER (or an API key) on the backend — with the default MOCK provider it works offline."
          : "Something went wrong answering that. Try again." };
        return copy;
      });
    },
  });

  const submit = (text: string) => {
    const msg = text.trim();
    if (!msg || send.isPending) return;
    setDraft("");
    send.mutate(msg);
  };

  return (
    <div className="flex h-[calc(100vh-9rem)] flex-col">
      <PageHeader
        title="AI Assistant"
        sub="Answers are grounded in your stored analytics only. It won't invent numbers, and says so when data is missing."
        actions={
          <>
            <select className="field h-8 w-44 text-[12px]" value={conversationId ?? ""} onChange={(e) => { setConversationId(e.target.value || undefined); if (!e.target.value) setThread([]); }}>
              <option value="">New conversation</option>
              {(convs.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
            <Button variant="ghost" size="sm" onClick={() => { setConversationId(undefined); setThread([]); }}>Reset</Button>
          </>
        }
      />

      <div ref={scrollRef} className="card flex-1 space-y-4 overflow-y-auto p-4">
        {thread.length === 0 && (
          <div className="mx-auto max-w-md py-10 text-center">
            <p className="text-[13px] text-content-muted">Ask about your footprint, score, anomalies or what to do next.</p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              {SUGGESTIONS.map((s) => (
                <button key={s} onClick={() => submit(s)} className="chip border-line hover:bg-surface-3">{s}</button>
              ))}
            </div>
          </div>
        )}
        {thread.map((m, i) => (
          <div key={i} className={cn("flex", m.role === "USER" ? "justify-end" : "justify-start")}>
            <div className={cn(
              "max-w-[76%] rounded-md px-3.5 py-2.5 text-[13px] leading-relaxed",
              m.role === "USER" ? "bg-brand-strong text-paper dark:text-ink" : "border border-line bg-surface-2 text-content",
            )}>
              {m.pending ? <span className="flex items-center gap-2 text-content-dim"><Spinner className="h-3.5 w-3.5" /> thinking…</span>
                : <p className="whitespace-pre-line">{m.content}</p>}
              {m.role === "ASSISTANT" && !m.pending && (m.disclaimers?.length || m.model) && (
                <div className="mt-2 space-y-1 border-t border-line pt-2">
                  {m.disclaimers?.map((d, j) => <p key={j} className="text-[10.5px] text-content-dim">{d}</p>)}
                  <div className="flex items-center gap-1.5">
                    {m.model && <span className="font-mono text-[9.5px] uppercase tracking-wider text-content-dim">{m.model}</span>}
                    {m.grounded === false && <Badge tone="signal">kept to verified figures</Badge>}
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); submit(draft); }}>
        <input
          className="field flex-1"
          placeholder="Ask a question about your environmental data…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          disabled={send.isPending}
        />
        <Button type="submit" loading={send.isPending} disabled={!draft.trim()}>Send</Button>
      </form>
    </div>
  );
}
