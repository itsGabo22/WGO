"use client";

import React, { useEffect } from "react";
import { motion } from "framer-motion";
import { useTelemetry } from "@/hooks/useTelemetry";
import { NextTurnIndicator } from "./NextTurnIndicator";
import { AbstractMinimap } from "./AbstractMinimap";

import { X } from "lucide-react";

interface ScreenActiveNavigationProps {
  isActive: boolean;
  onCancel: () => void;
  onArrived: () => void;
}

export function ScreenActiveNavigation({ isActive, onCancel, onArrived }: ScreenActiveNavigationProps) {
  const { data: telemetry, isFinished, progress } = useTelemetry(isActive);

  useEffect(() => {
    if (isFinished) {
      onArrived();
    }
  }, [isFinished, onArrived]);

  if (!telemetry) return null;

  return (
    <motion.div 
      className="flex h-full w-full flex-col justify-between py-8 px-6"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.3 }}
    >
      {/* TOP: Direction, Distance, Instruction */}
      <div className="flex flex-col items-center justify-start gap-1">
        <NextTurnIndicator 
          instruction={telemetry.instruction}
          distance={telemetry.distanceToTurn}
          turnDirection={telemetry.turnDirection}
        />
      </div>

      {/* CENTER: SVG Minimap */}
      <div className="flex-1 flex flex-col items-center justify-center w-full my-2">
        <AbstractMinimap progress={progress} />
      </div>

      {/* BOTTOM: Cancel Button */}
      <div className="flex flex-col items-center justify-end w-full pb-2">
        <button 
          onClick={onCancel}
          className="flex h-[48px] min-w-[120px] items-center justify-center gap-2 rounded-full bg-surface/80 border border-neutral/10 text-black active:scale-95 transition-transform px-4"
          aria-label="Cancelar navegación"
        >
          <X size={20} strokeWidth={2.5} />
          <span className="text-[14px] font-bold tracking-widest">CANCELAR</span>
        </button>
      </div>
    </motion.div>
  );
}
