-- Seed curated S1 snippets (author_id = NULL = system content)
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Four on the Floor', 'Classic 4/4 house kick pattern', '(track :kick (seq :bd :bd :bd :bd))', '{rhythm,house}', 124);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Boom-Bap', 'Classic hip-hop kick and snare groove', '(track :boom-bap
  (stack
    (seq :bd :_ :_ :bd :_ :_ :bd :_)
    (seq :_ :_ :sd :_ :_ :_ :sd :_)))', '{rhythm,breakbeat}', 90);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Amen Break', 'Classic breakbeat from the Amen Brother', '(track :amen
  (seq :bd :_ :_ :bd :_ :_ :sd :_
       :bd :_ :bd :_ :_ :sd :_ :_))', '{rhythm,dnb,breakbeat}', 165);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Minimal Techno', 'Sparse techno kick with offbeat hats', '(track :minimal
  (stack
    (seq :bd :_ :_ :_ :bd :_ :_ :_)
    (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)
         (amp 0.4))))', '{rhythm,techno,minimal}', 130);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Euclidean 5/8 Kick', 'Björklund algorithm: 5 onsets in 8 steps', '(track :kick (euclidean 5 8 :bd))', '{rhythm,euclidean,polyrhythm}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Polyrhythm 3:4', 'Three against four: kick triplets over snare quarters', '(track :poly
  (stack
    (fast 3 (seq :bd :_ :_))
    (fast 4 (seq :_ :sd :_ :sd))))', '{rhythm,polyrhythm}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Clave 3-2', 'Afro-Cuban 3-2 clave timeline pattern', '(track :clave
  (seq :bd :_ :_ :bd :_ :_ :bd :_
       :_ :_ :bd :_ :_ :bd :_ :_))', '{rhythm,percussive}', 110);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Acid 303 Line', 'Squelchy acid bassline with fast decay', '(track :acid
  (->> (scale :minor :c2 (seq 0 :_ 0 3 :_ 5 3 :_))
       (decay 0.12)
       (amp 0.8)))', '{bassline,techno,house}', 130);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Sub Bass Walk', 'Deep sub bass with ascending minor walk', '(track :sub
  (->> (scale :minor :c1 (seq 0 3 5 7))
       (slow 2)
       (amp 0.9)
       (decay 0.3)))', '{bassline,minimal}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Reese Bass', 'Classic DnB detuned synth bassline', '(track :reese
  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))
       (amp 0.9)
       (decay 0.2)))', '{bassline,dnb}', 174);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Walking Bass', 'Jazz-style stepwise bass movement', '(track :walk
  (->> (scale :major :c2 (seq 0 2 4 5 3 1 0 :_))
       (decay 0.6)
       (amp 0.7)))', '{bassline,jazz}', 130);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Offbeat Bass', 'Reggae-style bass hitting on the offbeats', '(track :bass
  (->> (scale :minor :c2 (seq :_ 0 :_ 0))
       (amp 0.8)
       (attack 0.01)
       (decay 0.4)))', '{bassline}', 140);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Minor Arpeggio', 'Ascending minor arpeggio pattern', '(track :arp
  (->> (scale :minor :c4 (seq 0 3 7 0))
       (fast 2)
       (amp 0.5)
       (decay 0.3)))', '{melody,chord-progression}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Chromatic Descent', 'Stepwise descending line through minor scale', '(track :descent
  (->> (scale :minor :a4 (seq 7 6 5 4 3 2 1 0))
       (slow 2)
       (amp 0.5)
       (decay 0.5)))', '{melody,lead}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Pentatonic Loop', 'Smooth pentatonic melodic phrase', '(track :penta
  (->> (scale :pentatonic :a4 (seq 0 1 2 3 4 3 2 1))
       (amp 0.6)
       (decay 0.4)))', '{melody,lead}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Blues Scale Riff', 'Gritty minor-pentatonic blues lick', '(track :blues
  (->> (scale :minor-pentatonic :a3 (seq 0 2 3 4 3 2 0 :_))
       (amp 0.6)
       (decay 0.4)))', '{melody,lead}', 100);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Algorithmic Lead', 'Minor scale phrase reversed every 3 cycles', '(track :lead
  (->> (every 3 rev (scale :minor :c5 (seq 0 2 5 7 5 2)))
       (amp 0.5)
       (decay 0.35)))', '{melody,lead,technique}', 110);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Major 7 Progression', 'I-IV major 7th chord movement', '(track :chords
  (->> (cat (chord :major7 :c4) (chord :major7 :f4))
       (amp 0.4)
       (attack 0.05)
       (decay 1.5)))', '{chord-progression}', 90);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Dreamy Pad', 'Slow-moving major 7 pad with long attack', '(track :pad
  (->> (chord :major7 :d4)
       (slow 4)
       (amp 0.3)
       (attack 0.4)
       (decay 3.0)))', '{chord-progression,ambient,pad}', 72);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'ii-V-I Jazz Turnaround', 'Classic jazz ii-V-I cadence', '(track :jazz
  (->> (cat (chord :minor7 :d4) (chord :dom7 :g3) (chord :major7 :c4))
       (slow 2)
       (amp 0.35)
       (attack 0.03)
       (decay 1.0)))', '{chord-progression,jazz}', 130);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Reverb Tail', 'Sparse snare hit with long cathedral reverb', '(track :reverb-demo
  (->> (seq :sd :_ :_ :_ :_ :_ :_ :_)
       (amp 0.6)
       (fx :reverb :wet 0.9 :size 0.9)))', '{fx-demo,ambient}', 80);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Delay Feedback', 'Hi-hat with rhythmic dotted-eighth delay echo', '(track :delay-demo
  (->> (seq :hh :_ :_ :hh :_ :_ :hh :_)
       (amp 0.5)
       (fx :delay :time 0.375 :feedback 0.5 :wet 0.6)))', '{fx-demo}', 120);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Dattorro Shimmer', 'Sparse melody through ethereal shimmer reverb', '(track :shimmer
  (->> (scale :major :c5 (seq 0 :_ :_ 4 :_ :_ 7 :_))
       (slow 2)
       (amp 0.4)
       (attack 0.1)
       (decay 2.0)
       (fx :dattorro :wet 0.8 :shimmer 0.6)))', '{fx-demo,ambient}', 72);
INSERT INTO public.snippets (author_id, title, description, code, tags, bpm) VALUES (NULL, 'Filter Sweep', 'Drum loop with resonant lowpass filter', '(track :filter-demo
  (->> (fast 2 (seq :bd :hh :sd :hh))
       (amp 0.7)
       (fx :filter :value 600 :resonance 6.0)))', '{fx-demo,technique}', 120);
