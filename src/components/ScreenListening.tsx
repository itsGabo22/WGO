"use client";

import React, { useState, useEffect } from "react";
import { PrimaryMicButton } from "./PrimaryMicButton";
import { MassiveButton } from "./MassiveButton";
import { X } from "lucide-react";
import { StatusBadge } from "./StatusBadge";

interface ScreenListeningProps {
  isListening: boolean;
  onToggleListen: () => void;
  onCancel: () => void;
}

export function ScreenListening({ isListening, onToggleListen, onCancel }: ScreenListeningProps) {
  const [showRouting, setShowRouting] = useState(false);

  useEffect(() => {
    if (isListening) {
      const timer = setTimeout(() => {
        setShowRouting(true);
      }, 1500);
      return () => clearTimeout(timer);
    } else {
      setShowRouting(false);
    }
  }, [isListening]);

  const statusText = showRouting ? "TRAZANDO RUTA..." : "ESCUCHANDO...";

  return (
    <div className="flex h-full w-full flex-col items-center justify-between py-6">
      {/* Top Zone */}
      <div className="flex flex-col items-center gap-1 mt-4">
        <StatusBadge variant="neutral">WGO • NAV</StatusBadge>
        <h1 className="mt-2 text-center text-[20px] font-bold leading-tight tracking-tight text-black px-4">
          {isListening ? (
            <span className="text-primary text-[13px] tracking-widest uppercase">{statusText}</span>
          ) : (
            "¿A dónde vamos?"
          )}
        </h1>
      </div>

      {/* Center Zone */}
      <div className="flex-1 flex items-center justify-center">
        <PrimaryMicButton isListening={isListening} onClick={onToggleListen} />
      </div>

      {/* Bottom Zone */}
      <div className="mb-2">
        <MassiveButton variant="secondary" onClick={onCancel} className="h-[52px] min-w-[140px] text-[13px] tracking-widest">
          <X size={16} strokeWidth={3} />
          CANCELAR
        </MassiveButton>
      </div>
    </div>
  );
}
